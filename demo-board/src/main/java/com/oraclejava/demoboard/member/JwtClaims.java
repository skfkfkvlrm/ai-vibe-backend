package com.oraclejava.demoboard.member;

/** 검증을 통과한 JWT에서 꺼낸 값. */
public record JwtClaims(Long memberId, String role) {
}
