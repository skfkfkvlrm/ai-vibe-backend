package com.oraclejava.demoboard.member;

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

	/** 아이디가 없든 비밀번호가 틀리든 같은 401을 던진다. */
	public MemberEntity authenticate(String loginId, String password) {
		MemberEntity member = memberRepository.findByLoginId(loginId).orElse(null);
		String hash = member != null ? member.getPassword() : dummyHash;
		boolean matches = passwordEncoder.matches(password, hash);
		if (member == null || !matches) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
		}
		return member;
	}

	/** 토큰의 회원이 삭제되었다면 401로 취급한다. */
	public MemberResponse findForToken(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"))
				.toResponse();
	}
}
