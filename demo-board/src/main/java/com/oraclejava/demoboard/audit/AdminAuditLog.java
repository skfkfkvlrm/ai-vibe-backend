package com.oraclejava.demoboard.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자 작업·로그인 잠금 감사 기록. 조회만 가능하고 수정·삭제 경로는 없다. adminId가 null이면 시스템 기록이다. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

	public static final int MAX_DETAIL_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_id")
	private Long auditId;

	@Column(name = "admin_id")
	private Long adminId;

	@Column(name = "action", nullable = false, length = 40)
	private String action;

	@Column(name = "target_type", nullable = false, length = 20)
	private String targetType;

	@Column(name = "target_id")
	private Long targetId;

	@Column(name = "detail", length = MAX_DETAIL_LENGTH)
	private String detail;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected AdminAuditLog() {
	}

	public AdminAuditLog(Long adminId, String action, String targetType, Long targetId, String detail,
			LocalDateTime createdAt) {
		this.adminId = adminId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.detail = detail == null || detail.length() <= MAX_DETAIL_LENGTH ? detail
				: detail.substring(0, MAX_DETAIL_LENGTH);
		this.createdAt = createdAt;
	}

	public Long getAuditId() { return auditId; }
	public Long getAdminId() { return adminId; }
	public String getAction() { return action; }
	public String getTargetType() { return targetType; }
	public Long getTargetId() { return targetId; }
	public String getDetail() { return detail; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
