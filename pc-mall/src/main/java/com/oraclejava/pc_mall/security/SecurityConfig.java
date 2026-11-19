package com.oraclejava.pc_mall.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.oraclejava.pc_mall.member.JwtProvider;
import com.oraclejava.pc_mall.member.MemberRepository;

/**
 * 무상태 JWT 보안 설정.
 * <ul>
 * <li>공개: 회원가입·로그인, 상품 조회(GET), 헬스 체크, /error</li>
 * <li>상품 쓰기(POST·PUT·DELETE)와 /api/admin/**(회원 관리)는 ADMIN 전용</li>
 * <li>그 외 모든 경로는 로그인 필요(/api/auth/me 포함)</li>
 * </ul>
 * 쿠키가 아닌 Bearer 토큰을 쓰므로 CSRF는 끈다.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
			MemberRepository memberRepository) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
						// 상품 조회는 공개, 쓰기(등록·수정·삭제)는 ADMIN 전용이다. 비로그인은 401, 일반 회원은 403.
						.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
						.requestMatchers("/api/products", "/api/products/**").hasRole("ADMIN")
						// 회원 관리(목록·역할 변경·밴)는 ADMIN 전용.
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						// application.properties의 management.endpoints.web.base-path=/ 이므로 헬스는 /health 이다.
						.requestMatchers("/health", "/health/**", "/actuator/health", "/actuator/health/**", "/livez",
								"/readyz")
						.permitAll()
						// 서버 내부 오류 디스패치(/error)를 막으면 404·400 등이 401로 덮인다.
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(new JwtAuthenticationFilter(jwtProvider, memberRepository),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
