package com.oraclejava.demoboard.security;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비회원 쓰기 속도 제한: IP당 글은 3분에 1개, 답글은 1분에 3개. */
@Service
@Transactional(readOnly = true)
public class WriteRateLimitService {

	public enum Kind {
		POST(1, Duration.ofMinutes(3)), REPLY(3, Duration.ofMinutes(1));

		private final int max;
		private final Duration window;

		Kind(int max, Duration window) {
			this.max = max;
			this.window = window;
		}
	}

	private final WriteRateLimitRepository repository;
	/** board.guest-rate-limit.enabled=false로 끌 수 있다(기본 켜짐). 한 테스트에서 같은 IP로 글을 여러 개 쓰는 기존 테스트용. */
	private volatile boolean enabled;

	public WriteRateLimitService(WriteRateLimitRepository repository,
			@Value("${board.guest-rate-limit.enabled:true}") boolean enabled) {
		this.repository = repository;
		this.enabled = enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** 한도를 넘었으면 다시 쓸 수 있을 때까지 남은 초, 아니면 0. */
	public long retryAfterSeconds(Kind kind, String ip) {
		if (!enabled) {
			return 0;
		}
		LocalDateTime now = LocalDateTime.now();
		List<WriteRateLimitEntity> recent = repository
				.findByIpAndKindAndCreatedAtAfterOrderByCreatedAtAsc(ip, kind.name(), now.minus(kind.window));
		if (recent.size() < kind.max) {
			return 0;
		}
		// 가장 최근 max개 중 가장 오래된 기록이 창 밖으로 나가면 한 자리가 빈다.
		LocalDateTime frees = recent.get(recent.size() - kind.max).getCreatedAt().plus(kind.window);
		return Math.max(1, Duration.between(now, frees).toSeconds());
	}

	@Transactional
	public void record(Kind kind, String ip) {
		if (!enabled) {
			return;
		}
		repository.save(new WriteRateLimitEntity(ip, kind.name(), LocalDateTime.now()));
	}
}
