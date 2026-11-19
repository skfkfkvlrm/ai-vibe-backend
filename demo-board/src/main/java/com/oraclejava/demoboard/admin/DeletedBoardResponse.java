package com.oraclejava.demoboard.admin;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 관리자용 삭제된 글·답글 한 건. 비밀번호(해시)는 포함하지 않는다.
 * root_deleted는 답글의 원글도 삭제 상태인지(이 경우 답글만 복구해도 화면에는 보이지 않는다). 원글은 항상 false.
 */
public record DeletedBoardResponse(
		@JsonProperty("board_num") Long boardNum,
		@JsonProperty("board_name") String boardName,
		@JsonProperty("board_subject") String boardSubject,
		@JsonProperty("board_content") String boardContent,
		@JsonProperty("re_ref") Long reRef,
		@JsonProperty("re_lev") Integer reLev,
		@JsonProperty("member_id") Long memberId,
		@JsonProperty("board_date") LocalDateTime boardDate,
		@JsonProperty("deleted_at") LocalDateTime deletedAt,
		@JsonProperty("root_deleted") boolean rootDeleted) {
}
