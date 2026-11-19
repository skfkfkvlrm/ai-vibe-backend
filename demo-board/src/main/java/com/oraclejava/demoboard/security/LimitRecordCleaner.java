package com.oraclejava.demoboard.security;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패·비회원 쓰기 제한 기록 중 1일 지난 행을 주기적으로 지운다(1시간마다, 시작 5분 뒤).
 * 감사 로그(admin_audit_log)는 정리하지 않는다. 한 대의 서버만 돌린다고 가정한다.
 */
@Component
public class LimitRecordCleaner {

	static final Duration RETENTION = Duration.ofDays(1);
	private static final Logger log = LoggerFactory.getLogger(LimitRecordCleaner.class);

	public record PurgeResult(int loginAttempts, int writeRateLimits) {
	}

	private final LoginAttemptRepository loginAttempts;
	private final WriteRateLimitRepository writeRateLimits;

	public LimitRecordCleaner(LoginAttemptRepository loginAttempts, WriteRateLimitRepository writeRateLimits) {
		this.loginAttempts = loginAttempts;
		this.writeRateLimits = writeRateLimits;
	}

	@Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
	public void run() {
		PurgeResult result = purge(LocalDateTime.now());
		if (result.loginAttempts() > 0 || result.writeRateLimits() > 0) {
			log.info("제한 기록 정리: login_attempt {}건, write_rate_limit {}건 삭제", result.loginAttempts(),
					result.writeRateLimits());
		}
	}

	@Transactional
	public PurgeResult purge(LocalDateTime now) {
		LocalDateTime cutoff = now.minus(RETENTION);
		return new PurgeResult(loginAttempts.deleteStale(cutoff), writeRateLimits.deleteOlderThan(cutoff));
	}
}
