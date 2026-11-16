package com.oraclejava.demodog.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.oraclejava.demodog.dog.DogRepository;

/**
 * dogs 테이블을 실제로 조회해 개 데이터 저장소의 가용성을 점검하는 커스텀 Indicator.
 * /actuator/health 호출 시 빈 이름에서 'HealthIndicator'를 제외한 'dogStore' 키로 노출된다.
 */
@Component
public class DogStoreHealthIndicator implements HealthIndicator {

	private final DogRepository dogRepository;

	public DogStoreHealthIndicator(DogRepository dogRepository) {
		this.dogRepository = dogRepository;
	}

	@Override
	public Health health() {
		long start = System.nanoTime();
		try {
			long count = dogRepository.count();
			long latencyMs = (System.nanoTime() - start) / 1_000_000;
			return Health.up()
					.withDetail("table", "dogs")
					.withDetail("dogCount", count)
					.withDetail("latencyMs", latencyMs)
					.build();
		} catch (Exception e) {
			return Health.down(e)
					.withDetail("table", "dogs")
					.build();
		}
	}
}
