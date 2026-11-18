package com.oraclejava.demo_boot.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 전사 표준 헬스체크 가이드라인(common_guardrails.md 제9장) 준수 커스텀 Indicator.
 * /actuator/health 호출 시 빈 이름에서 'HealthIndicator'를 제외한 'customService' 키로 노출됩니다.
 */
@Component
public class CustomServiceHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            // 실무형 서비스 레이턴시 및 가용성 체크 시뮬레이션
            long latencyMs = 12L;
            boolean isHealthy = true;

            if (isHealthy) {
                return Health.up()
                        .withDetail("service", "MockPaymentGateway")
                        .withDetail("latencyMs", latencyMs)
                        .withDetail("circuitBreaker", "CLOSED")
                        .build();
            } else {
                return Health.down()
                        .withDetail("service", "MockPaymentGateway")
                        .withDetail("reason", "Gateway timeout")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("service", "MockPaymentGateway")
                    .build();
        }
    }
}
