package com.oraclejava.demoboard.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberRepository;

/**
 * 감사 로그 기록·조회. 기록은 호출한 작업과 같은 트랜잭션에 참여하므로 작업이 롤백되면 기록도 롤백된다.
 * 비밀번호·토큰 같은 비밀은 detail에 넣지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

	public static final String BOARD_RESTORE = "BOARD_RESTORE";
	public static final String BOARD_EDIT = "BOARD_EDIT";
	public static final String BOARD_DELETE = "BOARD_DELETE";
	public static final String MEMBER_BAN = "MEMBER_BAN";
	public static final String MEMBER_UNBAN = "MEMBER_UNBAN";
	public static final String MEMBER_PASSWORD_RESET = "MEMBER_PASSWORD_RESET";
	public static final String LOGIN_LOCKED = "LOGIN_LOCKED";

	/** 조회 시 한 번에 돌려주는 최대 건수(최신순). */
	private static final int LIST_LIMIT = 200;

	private final AdminAuditLogRepository repository;
	private final MemberRepository memberRepository;

	public AuditService(AdminAuditLogRepository repository, MemberRepository memberRepository) {
		this.repository = repository;
		this.memberRepository = memberRepository;
	}

	@Transactional
	public void record(Long adminId, String action, String targetType, Long targetId, String detail) {
		repository.save(new AdminAuditLog(adminId, action, targetType, targetId, detail, LocalDateTime.now()));
	}

	public List<AuditLogResponse> latest() {
		List<AdminAuditLog> logs = repository.findAllByOrderByAuditIdDesc(PageRequest.of(0, LIST_LIMIT));
		Set<Long> adminIds = logs.stream().map(AdminAuditLog::getAdminId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, String> loginIds = memberRepository.findAllById(adminIds).stream()
				.collect(Collectors.toMap(MemberEntity::getMemberId, MemberEntity::getLoginId, (a, b) -> a));
		return logs.stream().map(l -> new AuditLogResponse(l.getAuditId(), l.getAdminId(),
				l.getAdminId() == null ? null : loginIds.get(l.getAdminId()), l.getAction(), l.getTargetType(),
				l.getTargetId(), l.getDetail(), l.getCreatedAt())).toList();
	}
}
