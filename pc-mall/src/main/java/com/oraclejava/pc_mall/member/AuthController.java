package com.oraclejava.pc_mall.member;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.security.ClientIp;
import com.oraclejava.pc_mall.security.CurrentMember;
import com.oraclejava.pc_mall.security.LimitResponse;
import com.oraclejava.pc_mall.security.LoginAttemptService;

import jakarta.servlet.http.HttpServletRequest;

/** 회원가입·로그인·내 정보. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final MemberService memberService;
	private final JwtProvider jwtProvider;
	private final LoginAttemptService loginAttempts;

	public AuthController(MemberService memberService, JwtProvider jwtProvider, LoginAttemptService loginAttempts) {
		this.memberService = memberService;
		this.jwtProvider = jwtProvider;
		this.loginAttempts = loginAttempts;
	}

	@PostMapping("/signup")
	public ResponseEntity<MemberResponse> signup(@RequestBody SignupRequest request) {
		if (request == null) {
			throw badRequest("request body is required");
		}
		MemberRules.loginId(request.loginId());
		MemberRules.password(request.password());
		String name = MemberRules.name(request.name());
		String email = MemberRules.email(request.email());
		try {
			MemberResponse created = memberService.signup(request.loginId(), request.password(), name, email);
			return ResponseEntity.created(URI.create("/api/auth/me")).body(created);
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 유니크 제약에 걸린 경우
			throw new ResponseStatusException(HttpStatus.CONFLICT, "login_id or email already exists");
		}
	}

	/**
	 * 아이디+IP별로 연속 5회 실패하면 5분간 429(LOGIN_LOCKED)를 돌려준다(올바른 비밀번호도 거부).
	 * 비밀번호가 맞아도 밴 중이면 토큰 대신 403과 밴 안내(code, reason, until, permanent)를 돌려준다.
	 */
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest http) {
		if (request == null || isBlank(request.loginId()) || isBlank(request.password())) {
			throw badRequest("login_id and password are required");
		}
		String ip = ClientIp.of(http);
		long locked = loginAttempts.lockedSeconds(request.loginId(), ip);
		if (locked > 0) {
			return LimitResponse.tooMany("LOGIN_LOCKED", locked);
		}
		MemberEntity member;
		try {
			member = memberService.authenticate(request.loginId(), request.password());
		} catch (ResponseStatusException e) {
			if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				recordFailureQuietly(request.loginId(), ip);
			}
			throw e;
		}
		loginAttempts.reset(request.loginId(), ip);
		if (member.isBanned(LocalDateTime.now())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BanResponse.of(member));
		}
		return ResponseEntity.ok(
				new TokenResponse(jwtProvider.issue(member), "Bearer", jwtProvider.getExpiration().toSeconds()));
	}

	/** 토큰 검증은 JwtAuthenticationFilter가 하고, 인증이 없으면 SecurityConfig가 401을 낸다. */
	@GetMapping("/me")
	public MemberResponse me(Principal principal) {
		Long memberId = CurrentMember.idOf(principal);
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
		}
		return memberService.findForToken(memberId);
	}

	/** 동시 요청으로 실패 기록이 겹쳐 제약 오류가 나도 로그인 응답(401)은 그대로 낸다. */
	private void recordFailureQuietly(String loginId, String ip) {
		try {
			loginAttempts.recordFailure(loginId, ip);
		} catch (DataIntegrityViolationException ignored) {
			// 같은 아이디+IP의 첫 실패가 동시에 들어온 경우: 한 건은 기록되었으므로 무시한다.
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
