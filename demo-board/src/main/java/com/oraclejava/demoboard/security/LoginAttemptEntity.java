package com.oraclejava.demoboard.security;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 아이디+IP별 로그인 실패 횟수와 잠금 해제 시각. 존재하지 않는 아이디도 똑같이 기록한다. */
@Entity
@Table(name = "login_attempt", uniqueConstraints = @UniqueConstraint(name = "uk_login_attempt", columnNames = {
		"login_id", "ip" }))
public class LoginAttemptEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "attempt_id")
	private Long attemptId;

	@Column(name = "login_id", nullable = false, length = 50)
	private String loginId;

	@Column(name = "ip", nullable = false, length = 64)
	private String ip;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	/** 마지막으로 실패한 시각. 오래된 기록을 자동 정리하는 기준이며, 이 컬럼이 생기기 전의 행은 null이다. */
	@Column(name = "last_failed_at")
	private LocalDateTime lastFailedAt;

	protected LoginAttemptEntity() {
	}

	public LoginAttemptEntity(String loginId, String ip) {
		this.loginId = loginId;
		this.ip = ip;
	}

	public boolean isLocked(LocalDateTime now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	public long remainingSeconds(LocalDateTime now) {
		return Math.max(1, Duration.between(now, lockedUntil).toSeconds());
	}

	/** 실패 1회를 기록한다. 지난 잠금은 먼저 풀고 센다. 이번에 새로 잠겼으면 true. */
	public boolean fail(LocalDateTime now, int maxFailures, Duration lockDuration) {
		if (lockedUntil != null && !lockedUntil.isAfter(now)) {
			failedCount = 0;
			lockedUntil = null;
		}
		failedCount++;
		lastFailedAt = now;
		if (failedCount >= maxFailures) {
			lockedUntil = now.plus(lockDuration);
			return true;
		}
		return false;
	}

	public void reset() {
		failedCount = 0;
		lockedUntil = null;
	}

	public int getFailedCount() { return failedCount; }
}
