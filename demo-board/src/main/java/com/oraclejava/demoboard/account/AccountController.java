package com.oraclejava.demoboard.account;

import java.security.Principal;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.demoboard.board.Board;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberResponse;
import com.oraclejava.demoboard.member.MemberRules;
import com.oraclejava.demoboard.member.MemberService;
import com.oraclejava.demoboard.member.TokenResponse;
import com.oraclejava.demoboard.security.CurrentMember;

/** 회원 본인 API. 모두 로그인이 필요하다(SecurityConfig의 그 외 경로 규칙). */
@RestController
@RequestMapping("/api/members/me")
public class AccountController {

	private final AccountService accountService;
	private final MemberService memberService;
	private final JwtProvider jwtProvider;

	public AccountController(AccountService accountService, MemberService memberService, JwtProvider jwtProvider) {
		this.accountService = accountService;
		this.memberService = memberService;
		this.jwtProvider = jwtProvider;
	}

	/** 이름·이메일 수정. 이름을 바꾸면 이미 쓴 글의 작성자명도 함께 바뀐다. */
	@PatchMapping
	public MemberResponse updateProfile(@RequestBody ProfileRequest request, Principal principal) {
		Long memberId = requireMember(principal);
		if (request == null || (request.name() == null && request.email() == null)) {
			throw badRequest("name or email is required");
		}
		String name = request.name() != null ? MemberRules.name(request.name()) : null;
		String email = request.email() != null ? MemberRules.email(request.email()) : null;
		try {
			return accountService.updateProfile(memberId, name, email).toResponse();
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
		}
	}

	/**
	 * 비밀번호 변경. 이전에 발급된 토큰은 모두 무효가 되므로, 변경한 본인의 현재 로그인은
	 * 응답의 새 토큰으로 이어간다.
	 */
	@PostMapping("/password")
	public TokenResponse changePassword(@RequestBody PasswordChangeRequest request, Principal principal) {
		Long memberId = requireMember(principal);
		if (request == null || request.currentPassword() == null || request.currentPassword().isEmpty()) {
			throw badRequest("current_password is required");
		}
		MemberRules.password(request.newPassword());
		MemberEntity member = memberService.changePassword(memberId, request.currentPassword(), request.newPassword());
		return new TokenResponse(jwtProvider.issue(member), "Bearer", jwtProvider.getExpiration().toSeconds());
	}

	/** 탈퇴(익명화). 비밀번호를 다시 확인하며 되돌릴 수 없다. */
	@DeleteMapping
	public ResponseEntity<Void> withdraw(@RequestBody WithdrawRequest request, Principal principal) {
		Long memberId = requireMember(principal);
		if (request == null || request.password() == null || request.password().isEmpty()) {
			throw badRequest("password is required");
		}
		accountService.withdraw(memberId, request.password());
		return ResponseEntity.noContent().build();
	}

	/** 내가 쓴 글·답글(삭제된 글 제외). */
	@GetMapping("/posts")
	public List<Board> myPosts(Principal principal) {
		return accountService.myPosts(requireMember(principal));
	}

	private static Long requireMember(Principal principal) {
		Long memberId = CurrentMember.idOf(principal);
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
		}
		return memberId;
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
