package com.oraclejava.demoboard.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 이름·이메일 수정 요청. 보내지 않은(null) 항목은 바꾸지 않는다. */
public record ProfileRequest(
		@JsonProperty("name") String name,
		@JsonProperty("email") String email) {
}
