package com.oraclejava.demoboard.member;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 밴 중인 회원이 로그인할 때 돌려주는 안내(403). until이 null이면 영구 밴이다. */
public record BanResponse(
		@JsonProperty("code") String code,
		@JsonProperty("reason") String reason,
		@JsonProperty("until") LocalDateTime until,
		@JsonProperty("permanent") boolean permanent) {

	public static BanResponse of(MemberEntity member) {
		return new BanResponse("BANNED", member.getBanReason(), member.getBannedUntil(),
				member.getBannedUntil() == null);
	}
}
