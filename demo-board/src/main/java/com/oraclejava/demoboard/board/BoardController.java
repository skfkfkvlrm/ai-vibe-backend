package com.oraclejava.demoboard.board;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.demoboard.security.CurrentMember;

/**
 * 게시판 API. 조회는 공개이고, 쓰기·수정·삭제는 로그인 회원(Bearer 토큰) 또는 비회원(글별 비밀번호)이 할 수 있다.
 * 권한 판단은 BoardService가 한다.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

	private final BoardService boardService;

	public BoardController(BoardService boardService) {
		this.boardService = boardService;
	}

	@GetMapping
	public List<Board> list() {
		return boardService.findAll();
	}

	/** 단건 조회. 호출될 때마다 board_readcount가 1 증가한다. */
	@GetMapping("/{id}")
	public Board get(@PathVariable Long id) {
		return boardService.readAndCount(id);
	}

	/** 원글 작성. 로그인 상태면 이름·비밀번호는 필요 없다. */
	@PostMapping
	public ResponseEntity<Board> create(@RequestBody BoardRequest request, Principal principal) {
		Long memberId = CurrentMember.idOf(principal);
		validateCreate(request, memberId);
		Board created = boardService.create(request, memberId);
		return ResponseEntity.created(URI.create("/api/boards/" + created.boardNum())).body(created);
	}

	/** 답글 작성. */
	@PostMapping("/{id}/replies")
	public ResponseEntity<Board> reply(@PathVariable Long id, @RequestBody BoardRequest request,
			Principal principal) {
		Long memberId = CurrentMember.idOf(principal);
		validateCreate(request, memberId);
		Board created = boardService.reply(id, request, memberId);
		return ResponseEntity.created(URI.create("/api/boards/" + created.boardNum())).body(created);
	}

	@PutMapping("/{id}")
	public Board update(@PathVariable Long id, @RequestBody BoardRequest request, Principal principal) {
		if (request == null || isBlank(request.boardSubject())) throw badRequest("board_subject is required");
		return boardService.update(id, request, CurrentMember.idOf(principal));
	}

	@PatchMapping("/{id}")
	public Board patch(@PathVariable Long id, @RequestBody BoardRequest request, Principal principal) {
		if (request == null) {
			throw badRequest("request body is required");
		}
		if (request.boardName() != null && request.boardName().isBlank()) {
			throw badRequest("board_name must not be blank");
		}
		if (request.boardSubject() != null && request.boardSubject().isBlank()) {
			throw badRequest("board_subject must not be blank");
		}
		return boardService.patch(id, request, CurrentMember.idOf(principal));
	}

	/** 삭제(soft delete). 비회원 글은 X-Board-Pass 헤더가 필요하고, 회원 글은 본인 토큰이면 된다. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@RequestHeader(value = "X-Board-Pass", required = false) String pass, Principal principal) {
		boardService.delete(id, pass, CurrentMember.idOf(principal));
		return ResponseEntity.noContent().build();
	}

	/** 로그인 상태(memberId != null)면 제목만, 비회원이면 이름·비밀번호·제목이 필요하다. */
	private static void validateCreate(BoardRequest r, Long memberId) {
		if (r == null) throw badRequest("request body is required");
		if (isBlank(r.boardSubject())) throw badRequest("board_subject is required");
		if (memberId == null) {
			if (isBlank(r.boardName())) throw badRequest("board_name is required");
			if (isBlank(r.boardPass())) throw badRequest("board_pass is required");
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
