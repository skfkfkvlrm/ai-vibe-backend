package com.oraclejava.pc_mall.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.oraclejava.pc_mall.member.JwtClaims;
import com.oraclejava.pc_mall.member.JwtProvider;
import com.oraclejava.pc_mall.member.MemberEntity;
import com.oraclejava.pc_mall.member.MemberRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer 토큰을 검증하고, 인증된 요청마다 DB에서 회원의 현재 상태를 확인한다.
 * 서명·만료가 틀렸거나, 회원이 없거나, 토큰 버전(ver)이 달라졌거나(비밀번호 변경 이후), 밴 중이면
 * 인증을 만들지 않고 비로그인으로 통과시킨다. 인증이 필요한 경로(/api/auth/me)는 SecurityConfig가 401을 낸다.
 * 빈으로 등록하지 않고 SecurityConfig에서만 생성해 서블릿 필터로 중복 등록되지 않게 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtProvider jwtProvider;
	private final MemberRepository memberRepository;

	public JwtAuthenticationFilter(JwtProvider jwtProvider, MemberRepository memberRepository) {
		this.jwtProvider = jwtProvider;
		this.memberRepository = memberRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER)) {
			try {
				JwtClaims claims = jwtProvider.parse(header.substring(BEARER.length()).trim());
				MemberEntity member = memberRepository.findById(claims.memberId()).orElse(null);
				if (member != null && member.getTokenVersion() == claims.version()
						&& !member.isBanned(LocalDateTime.now())) {
					// principal은 회원 ID(Long). 권한은 토큰이 아니라 DB의 현재 role을 쓴다.
					var authentication = new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
							List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole())));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			} catch (JwtException | IllegalArgumentException e) {
				// 무효 토큰: 비로그인으로 계속 진행한다.
			}
		}
		chain.doFilter(request, response);
	}
}
