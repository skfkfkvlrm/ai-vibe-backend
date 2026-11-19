package com.oraclejava.demoboard.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.oraclejava.demoboard.member.JwtProvider;

/**
 * 무상태 JWT 보안 설정.
 * <ul>
 * <li>공개: 회원가입·로그인, 게시판 조회와 비회원 쓰기(비밀번호는 서비스가 검사), 헬스 체크, /error</li>
 * <li>그 외 모든 경로는 로그인 필요(/api/auth/me 포함)</li>
 * </ul>
 * 쿠키가 아닌 Bearer 토큰을 쓰므로 CSRF는 끈다.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
						.requestMatchers("/api/boards", "/api/boards/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**", "/livez", "/readyz").permitAll()
						// 서버 내부 오류 디스패치(/error)를 막으면 404·400 등이 401로 덮인다.
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
