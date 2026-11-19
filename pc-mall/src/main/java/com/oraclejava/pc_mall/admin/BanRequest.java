package com.oraclejava.pc_mall.admin;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 밴 요청. reason은 필수(500자 이하).
 * type: 1D / 7D / 30D(지금부터 N일), PERMANENT(영구), UNTIL(until에 지정한 미래 시각까지).
 */
public record BanRequest(
		@JsonProperty("reason") String reason,
		@JsonProperty("type") String type,
		@JsonProperty("until") LocalDateTime until) {
}
