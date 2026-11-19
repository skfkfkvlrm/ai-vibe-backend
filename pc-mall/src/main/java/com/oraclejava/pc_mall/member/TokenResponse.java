package com.oraclejava.pc_mall.member;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 로그인 응답. expires_in은 초 단위이다. */
public record TokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_in") long expiresIn) {
}
