package com.oraclejava.demoboard.board;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 목록 한 페이지. items는 해당 페이지의 원글과 그 답글을 평평하게 담는다(원글 최신순, 답글은 원글 바로 뒤에 re_step 순).
 * totalElements·totalPages는 원글 기준이다.
 */
public record BoardPage(
		@JsonProperty("items") List<Board> items,
		@JsonProperty("page") int page,
		@JsonProperty("size") int size,
		@JsonProperty("total_elements") long totalElements,
		@JsonProperty("total_pages") int totalPages) {
}
