package com.oraclejava.demoboard.member;

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

import com.oraclejava.demoboard.security.CurrentMember;

/** 회원가입·로그인·내 정보. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final MemberService memberService;
	private final JwtProvider jwtProvider;

	public AuthController(MemberService memberService, JwtProvider jwtProvider) {
		this.memberService = memberService;
		this.jwtProvider = jwtProvider;
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

	/** 비밀번호가 맞아도 밴 중이면 토큰 대신 403과 밴 안내(code, reason, until, permanent)를 돌려준다. */
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request) {
		if (request == null || isBlank(request.loginId()) || isBlank(request.password())) {
			throw badRequest("login_id and password are required");
		}
		MemberEntity member = memberService.authenticate(request.loginId(), request.password());
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
			throw unauthorized();
		}
		return memberService.findForToken(memberId);
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static ResponseStatusException unauthorized() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
	}
}
