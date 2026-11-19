package com.oraclejava.demoboard.audit;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 감사 로그 조회 응답. adminLoginId는 시스템 기록(관리자 없음)이면 null이다. */
public record AuditLogResponse(
		@JsonProperty("audit_id") Long auditId,
		@JsonProperty("admin_id") Long adminId,
		@JsonProperty("admin_login_id") String adminLoginId,
		@JsonProperty("action") String action,
		@JsonProperty("target_type") String targetType,
		@JsonProperty("target_id") Long targetId,
		@JsonProperty("detail") String detail,
		@JsonProperty("created_at") LocalDateTime createdAt) {
}
