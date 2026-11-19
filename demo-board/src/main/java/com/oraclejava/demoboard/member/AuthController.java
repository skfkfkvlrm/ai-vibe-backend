package com.oraclejava.demoboard.member;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.regex.Pattern;

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

/** 회원가입·로그인·내 정보. 게시판 API에는 아직 인증을 적용하지 않는다(이후 단계). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{4,20}$");
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int MIN_PASSWORD = 8;
	/** BCrypt는 72바이트까지만 비교하므로 그 이상은 받지 않는다. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private final MemberService memberService;
	private final JwtProvider jwtProvider;

	public AuthController(MemberService memberService, JwtProvider jwtProvider) {
		this.memberService = memberService;
		this.jwtProvider = jwtProvider;
	}

	@PostMapping("/signup")
	public ResponseEntity<MemberResponse> signup(@RequestBody SignupRequest request) {
		validateSignup(request);
		String name = request.name().trim();
		String email = request.email().trim().toLowerCase();
		try {
			MemberResponse created = memberService.signup(request.loginId(), request.password(), name, email);
			return ResponseEntity.created(URI.create("/api/auth/me")).body(created);
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 유니크 제약에 걸린 경우
			throw new ResponseStatusException(HttpStatus.CONFLICT, "login_id or email already exists");
		}
	}

	@PostMapping("/login")
	public TokenResponse login(@RequestBody LoginRequest request) {
		if (request == null || isBlank(request.loginId()) || isBlank(request.password())) {
			throw badRequest("login_id and password are required");
		}
		MemberEntity member = memberService.authenticate(request.loginId(), request.password());
		return new TokenResponse(jwtProvider.issue(member), "Bearer", jwtProvider.getExpiration().toSeconds());
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

	private static void validateSignup(SignupRequest r) {
		if (r == null || r.loginId() == null || !LOGIN_ID.matcher(r.loginId()).matches()) {
			throw badRequest("login_id must be 4-20 chars of a-z, 0-9, _");
		}
		if (r.password() == null || r.password().length() < MIN_PASSWORD
				|| r.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw badRequest("password must be at least 8 characters");
		}
		if (isBlank(r.name()) || r.name().trim().length() > 100) {
			throw badRequest("name is required");
		}
		if (isBlank(r.email()) || r.email().trim().length() > 200 || !EMAIL.matcher(r.email().trim()).matches()) {
			throw badRequest("email is invalid");
		}
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
