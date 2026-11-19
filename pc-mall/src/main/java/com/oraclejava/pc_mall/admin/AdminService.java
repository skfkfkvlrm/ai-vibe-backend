package com.oraclejava.pc_mall.admin;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.member.MemberEntity;
import com.oraclejava.pc_mall.member.MemberRepository;

/**
 * 관리자 회원 관리. 호출 권한(ADMIN)은 SecurityConfig가 검사하고, 이 클래스는 대상에 대한 안전 규칙을 지킨다.
 * <ul>
 * <li>자기 자신의 해제·밴 금지, ADMIN 대상 밴 금지(403)</li>
 * <li>마지막 ADMIN 해제 금지(409): ADMIN 행을 쓰기 잠금으로 읽어 동시 요청도 직렬화한다</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

	private static final int MAX_REASON_LENGTH = 500;

	private final MemberRepository memberRepository;

	public AdminService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	public List<AdminMemberResponse> members() {
		LocalDateTime now = LocalDateTime.now();
		return memberRepository.findAllByOrderByMemberIdAsc().stream().map(m -> AdminMemberResponse.from(m, now))
				.toList();
	}

	/** 같은 역할로의 변경은 바꾸지 않고 현재 상태를 돌려준다(멱등). */
	@Transactional
	public AdminMemberResponse changeRole(Long adminId, Long targetId, String role) {
		if (!MemberEntity.ROLE_USER.equals(role) && !MemberEntity.ROLE_ADMIN.equals(role)) {
			throw status(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
		}
		MemberEntity target = requireTarget(targetId);
		LocalDateTime now = LocalDateTime.now();
		if (role.equals(target.getRole())) {
			return AdminMemberResponse.from(target, now);
		}
		if (MemberEntity.ROLE_ADMIN.equals(role)) {
			if (target.isBanned(now)) {
				throw status(HttpStatus.CONFLICT, "Unban the member before promoting");
			}
		} else {
			if (targetId.equals(adminId)) {
				throw status(HttpStatus.FORBIDDEN, "Cannot demote yourself");
			}
			boolean otherAdminExists = memberRepository.findAllByRole(MemberEntity.ROLE_ADMIN).stream()
					.anyMatch(a -> !a.getMemberId().equals(targetId));
			if (!otherAdminExists) {
				throw status(HttpStatus.CONFLICT, "Cannot demote the last admin");
			}
		}
		target.changeRole(role);
		return AdminMemberResponse.from(memberRepository.saveAndFlush(target), now);
	}

	/** 영구(PERMANENT)·기간(1D/7D/30D)·직접 입력(UNTIL) 밴. 사유는 필수. 이미 밴이면 새 조건으로 덮어쓴다. */
	@Transactional
	public AdminMemberResponse ban(Long adminId, Long targetId, BanRequest request) {
		if (request == null || request.reason() == null || request.reason().isBlank()
				|| request.reason().trim().length() > MAX_REASON_LENGTH) {
			throw status(HttpStatus.BAD_REQUEST, "reason is required (max 500 chars)");
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime until = switch (request.type() == null ? "" : request.type()) {
			case "1D" -> now.plusDays(1);
			case "7D" -> now.plusDays(7);
			case "30D" -> now.plusDays(30);
			case "PERMANENT" -> null;
			case "UNTIL" -> {
				if (request.until() == null || !request.until().isAfter(now)) {
					throw status(HttpStatus.BAD_REQUEST, "until must be a future date-time");
				}
				yield request.until();
			}
			default -> throw status(HttpStatus.BAD_REQUEST, "type must be one of 1D, 7D, 30D, PERMANENT, UNTIL");
		};
		MemberEntity target = requireTarget(targetId);
		if (targetId.equals(adminId)) {
			throw status(HttpStatus.FORBIDDEN, "Cannot ban yourself");
		}
		if (MemberEntity.ROLE_ADMIN.equals(target.getRole())) {
			throw status(HttpStatus.FORBIDDEN, "Cannot ban an admin");
		}
		target.ban(request.reason().trim(), now, until);
		return AdminMemberResponse.from(memberRepository.saveAndFlush(target), now);
	}

	/** 밴이 아닌 회원에게도 200(멱등). */
	@Transactional
	public AdminMemberResponse unban(Long targetId) {
		MemberEntity target = requireTarget(targetId);
		target.unban();
		return AdminMemberResponse.from(memberRepository.saveAndFlush(target), LocalDateTime.now());
	}

	private MemberEntity requireTarget(Long targetId) {
		return memberRepository.findById(targetId)
				.orElseThrow(() -> status(HttpStatus.NOT_FOUND, "Member not found: " + targetId));
	}

	private static ResponseStatusException status(HttpStatus status, String message) {
		return new ResponseStatusException(status, message);
	}
}
