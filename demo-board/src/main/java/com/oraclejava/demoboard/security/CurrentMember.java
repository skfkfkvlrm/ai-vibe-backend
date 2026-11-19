package com.oraclejava.demoboard.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

/** 요청의 Principal에서 로그인한 회원 ID를 꺼낸다. 비로그인이면 null. */
public final class CurrentMember {

	private CurrentMember() {
	}

	public static Long idOf(Principal principal) {
		if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof Long id) {
			return id;
		}
		return null;
	}
}
