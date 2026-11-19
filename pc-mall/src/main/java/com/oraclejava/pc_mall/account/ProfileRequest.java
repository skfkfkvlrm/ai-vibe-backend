package com.oraclejava.pc_mall.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 내 정보 수정 요청. null인 항목은 바꾸지 않는다. */
public record ProfileRequest(
		@JsonProperty("name") String name,
		@JsonProperty("email") String email) {
}
