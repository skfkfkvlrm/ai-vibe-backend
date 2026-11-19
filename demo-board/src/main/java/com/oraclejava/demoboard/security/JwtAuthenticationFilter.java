package com.oraclejava.demoboard.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.oraclejava.demoboard.member.JwtClaims;
import com.oraclejava.demoboard.member.JwtProvider;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer 토큰이 유효하면 로그인 정보를 채운다. 유효하지 않은 토큰은 오류를 내지 않고
 * 비로그인으로 취급한다(공개 API는 만료된 토큰을 보내도 그대로 동작해야 하므로).
 * 빈으로 등록하지 않고 SecurityConfig에서만 생성해 서블릿 필터로 중복 등록되지 않게 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER)) {
			try {
				JwtClaims claims = jwtProvider.parse(header.substring(BEARER.length()).trim());
				// principal은 회원 ID(Long)다. 컨트롤러는 CurrentMember로 꺼낸다.
				var authentication = new UsernamePasswordAuthenticationToken(claims.memberId(), null,
						List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException e) {
				// 무효 토큰: 로그인하지 않은 요청으로 계속 진행한다.
			}
		}
		chain.doFilter(request, response);
	}
}
