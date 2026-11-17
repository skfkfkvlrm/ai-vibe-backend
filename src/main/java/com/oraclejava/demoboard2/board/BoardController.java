package com.oraclejava.demoboard2.board;

import java.net.URI;
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

	/** 원글 작성. */
	@PostMapping
	public ResponseEntity<Board> create(@RequestBody BoardRequest request) {
		validateCreate(request);
		Board created = boardService.create(request);
		return ResponseEntity.created(URI.create("/api/boards/" + created.boardNum())).body(created);
	}

	/** 답글 작성. */
	@PostMapping("/{id}/replies")
	public ResponseEntity<Board> reply(@PathVariable Long id, @RequestBody BoardRequest request) {
		validateCreate(request);
		Board created = boardService.reply(id, request);
		return ResponseEntity.created(URI.create("/api/boards/" + created.boardNum())).body(created);
	}

	@PutMapping("/{id}")
	public Board update(@PathVariable Long id, @RequestBody BoardRequest request) {
		validateCreate(request);
		return boardService.update(id, request);
	}

	@PatchMapping("/{id}")
	public Board patch(@PathVariable Long id, @RequestBody BoardRequest request) {
		if (request == null || isBlank(request.boardPass())) {
			throw badRequest("board_pass is required");
		}
		if (request.boardName() != null && request.boardName().isBlank()) {
			throw badRequest("board_name must not be blank");
		}
		if (request.boardSubject() != null && request.boardSubject().isBlank()) {
			throw badRequest("board_subject must not be blank");
		}
		return boardService.patch(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@RequestHeader(value = "X-Board-Pass", required = false) String pass) {
		if (isBlank(pass)) {
			throw badRequest("X-Board-Pass header is required");
		}
		boardService.delete(id, pass);
		return ResponseEntity.noContent().build();
	}

	private static void validateCreate(BoardRequest r) {
		if (r == null || isBlank(r.boardName())) throw badRequest("board_name is required");
		if (isBlank(r.boardPass())) throw badRequest("board_pass is required");
		if (isBlank(r.boardSubject())) throw badRequest("board_subject is required");
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
