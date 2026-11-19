package com.oraclejava.demoboard.member;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 회원 도메인 서비스. 비밀번호는 BCrypt 해시로만 저장한다. */
@Service
@Transactional(readOnly = true)
public class MemberService {

	/** 임시 비밀번호에 쓰는 문자(헷갈리는 0/O, 1/l/I 제외). */
	private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private static final int TEMP_PASSWORD_LENGTH = 12;
	private static final SecureRandom RANDOM = new SecureRandom();

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

	/** 아이디가 없든 비밀번호가 틀리든 탈퇴한 회원이든 같은 401을 던진다. 밴 여부는 호출한 쪽이 확인한다. */
	public MemberEntity authenticate(String loginId, String password) {
		MemberEntity member = memberRepository.findByLoginId(loginId).orElse(null);
		String hash = member != null ? member.getPassword() : dummyHash;
		boolean matches = passwordEncoder.matches(password, hash);
		if (member == null || !matches || member.isWithdrawn()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
		}
		return member;
	}

	/** 게시글 작성자명으로 쓸 회원 이름. 토큰은 유효하지만 회원이 없다면 401로 취급한다. */
	public String requireName(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"))
				.getName();
	}

	/** 토큰의 회원이 삭제되었다면 401로 취급한다. */
	public MemberResponse findForToken(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"))
				.toResponse();
	}

	// ---- 관리자 기능 ----

	public List<MemberEntity> findAll() {
		return memberRepository.findAllByOrderByMemberIdDesc();
	}

	/** untilOrNull이 null이면 영구 밴. 관리자·본인은 대상이 될 수 없다. */
	@Transactional
	public MemberEntity ban(Long actingAdminId, Long targetId, String reason, LocalDateTime untilOrNull) {
		MemberEntity target = findManageable(actingAdminId, targetId);
		target.ban(reason, LocalDateTime.now(), untilOrNull);
		return memberRepository.saveAndFlush(target);
	}

	@Transactional
	public MemberEntity unban(Long targetId) {
		MemberEntity target = memberRepository.findById(targetId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found: " + targetId));
		target.unban();
		return memberRepository.saveAndFlush(target);
	}

	/**
	 * 임시 비밀번호를 무작위로 만들어 적용한다. 기존 로그인은 모두 무효가 되고, 회원이 직접 바꿀 때까지
	 * 변경 안내 표시가 켜진다. 평문은 이 반환값으로만 한 번 전달하며 저장·로그하지 않는다.
	 */
	@Transactional
	public String resetPassword(Long actingAdminId, Long targetId) {
		MemberEntity target = findManageable(actingAdminId, targetId);
		StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
		for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
			sb.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
		}
		String temporary = sb.toString();
		target.changePassword(passwordEncoder.encode(temporary), true);
		memberRepository.saveAndFlush(target);
		return temporary;
	}

	/** 관리자 작업 대상 확인: 본인·관리자는 403, 탈퇴한 회원은 409, 없으면 404. */
	private MemberEntity findManageable(Long actingAdminId, Long targetId) {
		if (targetId.equals(actingAdminId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot target yourself");
		}
		MemberEntity target = memberRepository.findById(targetId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found: " + targetId));
		if (MemberEntity.ROLE_ADMIN.equals(target.getRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot target an admin");
		}
		if (target.isWithdrawn()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Member already withdrawn");
		}
		return target;
	}

	// ---- 회원 본인 기능 ----

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
		member.changePassword(passwordEncoder.encode(newPassword), false);
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

	/** 탈퇴(익명화). 비밀번호를 다시 확인하고 관리자는 탈퇴할 수 없다. */
	@Transactional
	public MemberEntity withdraw(Long memberId, String password) {
		MemberEntity member = requireMember(memberId);
		if (MemberEntity.ROLE_ADMIN.equals(member.getRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins cannot withdraw");
		}
		if (password == null || !passwordEncoder.matches(password, member.getPassword())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password does not match");
		}
		member.withdraw(LocalDateTime.now(), passwordEncoder.encode(UUID.randomUUID().toString()));
		return memberRepository.saveAndFlush(member);
	}

	private MemberEntity requireMember(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"));
	}
}
