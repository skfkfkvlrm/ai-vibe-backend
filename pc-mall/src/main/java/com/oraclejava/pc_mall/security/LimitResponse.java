package com.oraclejava.pc_mall.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 429 응답 본문. code는 LOGIN_LOCKED. */
public record LimitResponse(
		@JsonProperty("code") String code,
		@JsonProperty("retry_after_seconds") long retryAfterSeconds) {

	public static ResponseEntity<LimitResponse> tooMany(String code, long retryAfterSeconds) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
				.body(new LimitResponse(code, retryAfterSeconds));
	}
}
