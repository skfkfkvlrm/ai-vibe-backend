package com.oraclejava.demoboard.board;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 게시글 API 응답 본문. 비밀번호(board_pass)는 절대 포함하지 않는다. */
public record Board(
		@JsonProperty("board_num") Long boardNum,
		@JsonProperty("board_name") String boardName,
		@JsonProperty("board_subject") String boardSubject,
		@JsonProperty("board_content") String boardContent,
		@JsonProperty("board_file") String boardFile,
		@JsonProperty("re_ref") Long reRef,
		@JsonProperty("re_lev") Integer reLev,
		@JsonProperty("re_step") Integer reStep,
		@JsonProperty("board_readcount") Long boardReadcount,
		@JsonProperty("board_date") LocalDateTime boardDate) {
}
