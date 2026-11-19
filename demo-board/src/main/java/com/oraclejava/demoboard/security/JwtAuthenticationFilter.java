package com.oraclejava.demoboard.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.oraclejava.demoboard.member.JwtClaims;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer 토큰을 검증하고, 인증된 요청마다 DB에서 회원의 현재 상태를 확인한다.
 * <ul>
 * <li>서명·만료가 틀렸거나, 회원이 없거나 탈퇴했거나, 토큰 버전(ver)이 달라졌으면(비밀번호 변경·초기화 이후)
 * 무효 토큰이다. 조회 등은 오류 없이 비로그인으로 취급하지만(공개 API는 그대로 동작해야 하므로),
 * 쓰기 요청(POST/PUT/PATCH/DELETE, 로그인·가입 제외)은 401로 만료를 알린다.</li>
 * <li>밴 중인 회원은 공개 조회(GET /api/boards...)만 비로그인으로 통과시키고, 그 외 요청은 즉시 403(BANNED)이다.</li>
 * </ul>
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
			boolean valid = false;
			try {
				JwtClaims claims = jwtProvider.parse(header.substring(BEARER.length()).trim());
				MemberEntity member = memberRepository.findById(claims.memberId()).orElse(null);
				if (member != null && !member.isWithdrawn() && member.getTokenVersion() == claims.version()) {
					valid = true;
					if (member.isBanned(LocalDateTime.now())) {
						if (!isPublicRead(request)) {
							writeBanned(response, member);
							return;
						}
						// 공개 조회는 비로그인으로 통과
					} else {
						// principal은 회원 ID(Long). 권한은 토큰이 아니라 DB의 현재 role을 쓴다.
						var authentication = new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
								List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole())));
						SecurityContextHolder.getContext().setAuthentication(authentication);
					}
				}
			} catch (JwtException | IllegalArgumentException e) {
				// 무효 토큰: 아래에서 쓰기 요청이면 401, 그 외에는 비로그인으로 계속 진행한다.
			}
			if (!valid && isGuardedWrite(request)) {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				return;
			}
		}
		chain.doFilter(request, response);
	}

	/**
	 * 무효 토큰을 401로 돌려줄 쓰기 요청인가. 쓰기 API가 비회원에게 열려 있어도, 토큰을 들고 온 쪽은 로그인 상태로 보낸
	 * 요청이라 조용히 비회원 취급하면 400 등으로 어긋나므로 만료를 알려준다. 로그인·가입은 만료 토큰이 남은 사용자도
	 * 다시 들어올 수 있어야 하므로 제외한다.
	 */
	private static boolean isGuardedWrite(HttpServletRequest request) {
		String method = request.getMethod();
		boolean write = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)
				|| "DELETE".equals(method);
		String uri = request.getRequestURI();
		return write && !"/api/auth/login".equals(uri) && !"/api/auth/signup".equals(uri);
	}

	private static boolean isPublicRead(HttpServletRequest request) {
		return "GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/boards");
	}

	/** 밴 안내 본문: {"code":"BANNED","reason":...,"until":...,"permanent":...} */
	private static void writeBanned(HttpServletResponse response, MemberEntity member) throws IOException {
		boolean permanent = member.getBannedUntil() == null;
		String json = "{\"code\":\"BANNED\",\"reason\":" + quote(member.getBanReason()) + ",\"until\":"
				+ (permanent ? "null" : quote(member.getBannedUntil().toString())) + ",\"permanent\":" + permanent + "}";
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json);
	}

	private static String quote(String s) {
		if (s == null) return "null";
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}
}
