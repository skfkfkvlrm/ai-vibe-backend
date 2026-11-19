package com.oraclejava.pc_mall.admin;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oraclejava.pc_mall.member.MemberEntity;

/**
 * 관리자용 회원 한 건. 비밀번호(해시)·토큰 버전은 포함하지 않는다.
 * status는 ACTIVE / BANNED(기간 밴이 지났으면 ACTIVE). banned_permanent가 true이면 영구 밴이다.
 */
public record AdminMemberResponse(
		@JsonProperty("member_id") Long memberId,
		@JsonProperty("login_id") String loginId,
		@JsonProperty("name") String name,
		@JsonProperty("email") String email,
		@JsonProperty("role") String role,
		@JsonProperty("created_at") LocalDateTime createdAt,
		@JsonProperty("status") String status,
		@JsonProperty("ban_reason") String banReason,
		@JsonProperty("banned_until") LocalDateTime bannedUntil,
		@JsonProperty("banned_permanent") boolean bannedPermanent) {

	public static AdminMemberResponse from(MemberEntity m, LocalDateTime now) {
		boolean banned = m.isBanned(now);
		return new AdminMemberResponse(m.getMemberId(), m.getLoginId(), m.getName(), m.getEmail(), m.getRole(),
				m.getCreatedAt(), m.status(now), banned ? m.getBanReason() : null, banned ? m.getBannedUntil() : null,
				m.isBannedPermanently(now));
	}
}
