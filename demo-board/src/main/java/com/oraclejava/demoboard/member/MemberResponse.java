package com.oraclejava.demoboard.member;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 회원 API 응답 본문. 비밀번호(해시 포함)는 절대 포함하지 않는다. */
public record MemberResponse(
		@JsonProperty("member_id") Long memberId,
		@JsonProperty("login_id") String loginId,
		@JsonProperty("name") String name,
		@JsonProperty("email") String email,
		@JsonProperty("role") String role,
		@JsonProperty("created_at") LocalDateTime createdAt) {
}
