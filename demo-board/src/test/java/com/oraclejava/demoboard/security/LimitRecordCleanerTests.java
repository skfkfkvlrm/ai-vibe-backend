package com.oraclejava.demoboard.security;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 제한 기록 자동 정리: 1일 지난 행만 지우고, 최근·잠금 중인 행과 감사 로그는 남긴다. */
@SpringBootTest
class LimitRecordCleanerTests {

	@Autowired
	private LimitRecordCleaner cleaner;
	@Autowired
	private LoginAttemptService loginAttempts;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from login_attempt");
		jdbc.update("delete from write_rate_limit");
		jdbc.update("delete from admin_audit_log");
	}

	private void attempt(String loginId, int failed, LocalDateTime lockedUntil, LocalDateTime lastFailedAt) {
		jdbc.update("insert into login_attempt (login_id, ip, failed_count, locked_until, last_failed_at) values (?, ?, ?, ?, ?)",
				loginId, "10.0.0.1", failed, lockedUntil, lastFailedAt);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	/** T1: 25시간 전 행은 삭제, 방금 행과 잠금 중인 행은 유지, 감사 로그는 오래돼도 유지. */
	@Test
	void purgesOnlyRecordsOlderThanOneDay() {
		LocalDateTime now = LocalDateTime.now();
		jdbc.update("insert into write_rate_limit (ip, kind, created_at) values (?, ?, ?)", "10.0.0.1", "POST", now.minusHours(25));
		jdbc.update("insert into write_rate_limit (ip, kind, created_at) values (?, ?, ?)", "10.0.0.1", "REPLY", now.minusMinutes(1));
		attempt("old_partial", 3, null, now.minusHours(25));
		attempt("old_locked", 5, now.minusHours(24), now.minusHours(24).minusMinutes(5));
		attempt("recent_partial", 2, null, now.minusMinutes(2));
		attempt("recent_locked", 5, now.plusMinutes(3), now.minusMinutes(2));
		attempt("legacy_no_timestamp", 1, null, null);
		jdbc.update("insert into admin_audit_log (admin_id, action, target_type, target_id, detail, created_at) values (?, ?, ?, ?, ?, ?)",
				3L, "BOARD_RESTORE", "BOARD", 1L, null, now.minusDays(30));

		LimitRecordCleaner.PurgeResult result = cleaner.purge(now);

		Assertions.assertEquals(1, result.writeRateLimits());
		Assertions.assertEquals(3, result.loginAttempts());
		Assertions.assertEquals(1, count("write_rate_limit"));
		Assertions.assertEquals(2, count("login_attempt"));
		Assertions.assertEquals(2, jdbc.queryForObject(
				"select count(*) from login_attempt where login_id in ('recent_partial', 'recent_locked')", Integer.class));
		Assertions.assertEquals(1, count("admin_audit_log"));
	}

	/** T2: 실패를 기록하면 last_failed_at이 채워지고, 성공으로 초기화해도 5회 잠금 규칙은 그대로. */
	@Test
	void failureStampsLastFailedAtAndLockRuleIsUnchanged() {
		loginAttempts.recordFailure("stamp_user", "10.0.0.1");
		Assertions.assertNotNull(jdbc.queryForObject(
				"select last_failed_at from login_attempt where login_id = 'stamp_user'", LocalDateTime.class));

		loginAttempts.reset("stamp_user", "10.0.0.1");
		Assertions.assertEquals(0, jdbc.queryForObject(
				"select failed_count from login_attempt where login_id = 'stamp_user'", Integer.class));

		for (int i = 0; i < 4; i++) loginAttempts.recordFailure("stamp_user", "10.0.0.1");
		Assertions.assertEquals(0, loginAttempts.lockedSeconds("stamp_user", "10.0.0.1"));
		loginAttempts.recordFailure("stamp_user", "10.0.0.1");
		Assertions.assertTrue(loginAttempts.lockedSeconds("stamp_user", "10.0.0.1") > 0);
	}

	/** T3: 정리로 지워진 행은 처음부터 다시 센다(예전 실패 3회 + 새 실패 4회가 합쳐져 잠기지 않는다). */
	@Test
	void purgedRowsStartCountingFromZero() {
		LocalDateTime now = LocalDateTime.now();
		attempt("stale_user", 3, null, now.minusHours(30));
		cleaner.purge(now);
		Assertions.assertEquals(0, count("login_attempt"));

		for (int i = 0; i < 4; i++) loginAttempts.recordFailure("stale_user", "10.0.0.1");
		Assertions.assertEquals(0, loginAttempts.lockedSeconds("stale_user", "10.0.0.1"));
		loginAttempts.recordFailure("stale_user", "10.0.0.1");
		Assertions.assertTrue(loginAttempts.lockedSeconds("stale_user", "10.0.0.1") > 0);
	}

	/** T4: 지울 행이 없어도 오류 없이 0건. */
	@Test
	void purgeWithNothingToDeleteIsHarmless() {
		LimitRecordCleaner.PurgeResult result = cleaner.purge(LocalDateTime.now());
		Assertions.assertEquals(0, result.loginAttempts());
		Assertions.assertEquals(0, result.writeRateLimits());
	}
}
