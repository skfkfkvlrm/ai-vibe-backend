package com.oraclejava.demoboard.member;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
		@JsonProperty("login_id") String loginId,
		@JsonProperty("password") String password) {
}
