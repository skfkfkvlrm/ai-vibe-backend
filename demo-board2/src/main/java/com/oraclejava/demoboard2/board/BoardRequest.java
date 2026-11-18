package com.oraclejava.demoboard2.board;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 게시글 작성/수정 요청 본문. board_num, re_ref, re_lev, re_step, board_readcount, board_date는
 * 서버가 관리하므로 받지 않는다. 수정 시 board_pass는 본인 확인용이다(비밀번호 변경 아님).
 */
public record BoardRequest(
		@JsonProperty("board_name") String boardName,
		@JsonProperty("board_pass") String boardPass,
		@JsonProperty("board_subject") String boardSubject,
		@JsonProperty("board_content") String boardContent,
		@JsonProperty("board_file") String boardFile) {
}
