package com.oraclejava.demoboard.member;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

/** 스프링 컨텍스트 없이 JwtProvider만 검증한다. */
class JwtProviderTests {

	private static final String SECRET = "unit-test-only-secret-key-0123456789abcdef";

	@Test
	void shortSecretIsRejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> new JwtProvider("too-short", 120));
	}

	@Test
	void roundTripAndTamperDetection() {
		JwtProvider provider = new JwtProvider(SECRET, 120);
		String token = provider.issue(7L, "USER", Duration.ofMinutes(5));
		Assertions.assertEquals(7L, provider.parseMemberId(token));

		// 다른 키로 서명된 토큰은 거부한다.
		JwtProvider other = new JwtProvider("another-unit-test-secret-key-0123456789ab", 120);
		Assertions.assertThrows(JwtException.class, () -> other.parseMemberId(token));
	}
}
