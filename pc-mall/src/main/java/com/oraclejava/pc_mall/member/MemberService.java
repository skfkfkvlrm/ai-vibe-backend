package com.oraclejava.pc_mall.member;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 회원 도메인 서비스. 비밀번호는 BCrypt 해시로만 저장한다. */
@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	/** 없는 아이디로 로그인할 때도 같은 해시 비교 비용을 들여 응답 시간 차이로 가입 여부를 알 수 없게 한다. */
	private final String dummyHash = passwordEncoder.encode("not-a-real-password");

	public MemberService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional
	public MemberResponse signup(String loginId, String password, String name, String email) {
		if (memberRepository.existsByLoginId(loginId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "login_id already exists");
		}
		if (memberRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
		}
		MemberEntity member = new MemberEntity(loginId, passwordEncoder.encode(password), name, email,
				LocalDateTime.now());
		return memberRepository.saveAndFlush(member).toResponse();
	}

	/** 아이디가 없든 비밀번호가 틀리든 같은 401을 던진다. 밴 여부는 호출한 쪽이 확인한다. */
	public MemberEntity authenticate(String loginId, String password) {
		MemberEntity member = memberRepository.findByLoginId(loginId).orElse(null);
		String hash = member != null ? member.getPassword() : dummyHash;
		boolean matches = passwordEncoder.matches(password, hash);
		if (member == null || !matches) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
		}
		return member;
	}

	/**
	 * 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 이전에 발급된 토큰은 모두 무효가 되므로
	 * 호출한 쪽은 반환된 회원으로 새 토큰을 발급해 현재 로그인을 이어간다.
	 */
	@Transactional
	public MemberEntity changePassword(Long memberId, String currentPassword, String newPassword) {
		MemberEntity member = requireMember(memberId);
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, member.getPassword())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current password does not match");
		}
		if (passwordEncoder.matches(newPassword, member.getPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from the current one");
		}
		member.changePassword(passwordEncoder.encode(newPassword));
		return memberRepository.saveAndFlush(member);
	}

	/** 이름·이메일 수정. null인 항목은 바꾸지 않는다. 이메일 중복은 409. */
	@Transactional
	public MemberEntity updateProfile(Long memberId, String newName, String newEmail) {
		MemberEntity member = requireMember(memberId);
		if (newEmail != null && !newEmail.equals(member.getEmail()) && memberRepository.existsByEmail(newEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
		}
		member.updateProfile(newName, newEmail);
		return memberRepository.saveAndFlush(member);
	}

	private MemberEntity requireMember(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"));
	}

	/** 토큰의 회원이 삭제되었다면 401로 취급한다. */
	public MemberResponse findForToken(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"))
				.toResponse();
	}
}
