package com.oraclejava.pc_mall.member;

/** 검증을 통과한 JWT에서 꺼낸 값. version은 토큰이 발급될 때의 회원 token_version이다(없으면 0). */
public record JwtClaims(Long memberId, String role, int version) {
}
