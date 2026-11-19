package com.oraclejava.demoboard.member;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 관리자 여부를 요청마다 DB의 현재 role로 판정한다. 토큰 안의 role은 신뢰하지 않으므로
 * 승격은 재로그인 없이 바로, 강등도 토큰 만료를 기다리지 않고 바로 반영된다.
 * SpEL에서는 {@code @memberAccess.isAdmin(authentication)}으로 쓴다.
 */
@Component
public class MemberAccess {

	private final MemberRepository memberRepository;

	public MemberAccess(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	public boolean isAdmin(Long memberId) {
		return memberId != null && memberRepository.findById(memberId)
				.map(member -> MemberEntity.ROLE_ADMIN.equals(member.getRole()))
				.orElse(false);
	}

	public boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getPrincipal() instanceof Long memberId && isAdmin(memberId);
	}
}
