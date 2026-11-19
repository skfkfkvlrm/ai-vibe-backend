package com.oraclejava.demoboard.member;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 회원가입 요청 본문. role 등 서버가 정하는 값은 받지 않는다. */
public record SignupRequest(
		@JsonProperty("login_id") String loginId,
		@JsonProperty("password") String password,
		@JsonProperty("name") String name,
		@JsonProperty("email") String email) {
}
