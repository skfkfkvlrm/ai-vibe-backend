package com.oraclejava.pc_mall.security;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 실패 제한: 아이디+IP별로 연속 5회 실패하면 5분 잠근다. 성공하면 횟수를 초기화한다. */
@Service
@Transactional(readOnly = true)
public class LoginAttemptService {

	static final int MAX_FAILURES = 5;
	static final Duration LOCK_DURATION = Duration.ofMinutes(5);
	private static final int MAX_LOGIN_ID_LENGTH = 50;

	private final LoginAttemptRepository repository;

	public LoginAttemptService(LoginAttemptRepository repository) {
		this.repository = repository;
	}

	/** 잠겨 있으면 남은 초, 아니면 0. */
	public long lockedSeconds(String loginId, String ip) {
		LocalDateTime now = LocalDateTime.now();
		return repository.findByLoginIdAndIp(key(loginId), ip)
				.filter(a -> a.isLocked(now))
				.map(a -> a.remainingSeconds(now))
				.orElse(0L);
	}

	@Transactional
	public void recordFailure(String loginId, String ip) {
		String id = key(loginId);
		LoginAttemptEntity attempt = repository.findByLoginIdAndIp(id, ip)
				.orElseGet(() -> new LoginAttemptEntity(id, ip));
		attempt.fail(LocalDateTime.now(), MAX_FAILURES, LOCK_DURATION);
		repository.saveAndFlush(attempt);
	}

	@Transactional
	public void reset(String loginId, String ip) {
		repository.findByLoginIdAndIp(key(loginId), ip).ifPresent(a -> {
			if (a.getFailedCount() > 0) {
				a.reset();
				repository.save(a);
			}
		});
	}

	private static String key(String loginId) {
		String id = loginId == null ? "" : loginId.trim();
		return id.length() > MAX_LOGIN_ID_LENGTH ? id.substring(0, MAX_LOGIN_ID_LENGTH) : id;
	}
}
