package com.oraclejava.demoboard.member;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT(HS256) 발급·검증. 서명 키는 반드시 설정(JWT_SECRET)에서 받고 코드에는 두지 않는다.
 * 키가 없거나 32바이트 미만이면 애플리케이션 기동이 실패한다.
 */
@Component
public class JwtProvider {

	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;
	private final Duration expiration;

	public JwtProvider(@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-minutes:120}") long expirationMinutes) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("jwt.secret(JWT_SECRET)은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
		this.key = Keys.hmacShaKeyFor(bytes);
		this.expiration = Duration.ofMinutes(expirationMinutes);
	}

	public Duration getExpiration() {
		return expiration;
	}

	public String issue(MemberEntity member) {
		return issue(member.getMemberId(), member.getRole(), expiration);
	}

	/** 만료 시간을 지정해 발급한다(테스트에서 만료 토큰을 만들 때도 사용). */
	String issue(Long memberId, String role, Duration validFor) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim("role", role)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + validFor.toMillis()))
				.signWith(key)
				.compact();
	}

	/** 서명·만료를 검증하고 회원 ID를 반환한다. 유효하지 않으면 {@link JwtException}. */
	public Long parseMemberId(String token) {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		try {
			return Long.valueOf(claims.getSubject());
		} catch (NumberFormatException e) {
			throw new JwtException("invalid subject", e);
		}
	}
}
