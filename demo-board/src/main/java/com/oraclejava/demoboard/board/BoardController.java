package com.oraclejava.demoboard.board;

import java.net.URI;
import java.security.Principal;
import java.util.Set;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.demoboard.security.ClientIp;
import com.oraclejava.demoboard.security.CurrentMember;
import com.oraclejava.demoboard.security.LimitResponse;
import com.oraclejava.demoboard.security.WriteRateLimitService;
import com.oraclejava.demoboard.security.WriteRateLimitService.Kind;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 게시판 API. 조회는 공개이고, 쓰기·수정·삭제는 로그인 회원(Bearer 토큰) 또는 비회원(글별 비밀번호)이 할 수 있다.
 * 권한 판단은 BoardService가 한다.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

	private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);
	private static final int MAX_QUERY_LENGTH = 100;

	private final BoardService boardService;
	private final WriteRateLimitService rateLimit;

	public BoardController(BoardService boardService, WriteRateLimitService rateLimit) {
		this.boardService = boardService;
		this.rateLimit = rateLimit;
	}

	/**
	 * 원글 기준 페이징 목록(답글은 해당 원글 뒤에 동봉). page는 0부터, size는 10·20·50(기본 10),
	 * field는 TITLE_CONTENT(기본)·AUTHOR, q가 비어 있으면 검색하지 않는다(최대 100자).
	 */
	@GetMapping
	public BoardPage list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String field,
			@RequestParam(required = false) String q) {
		if (page < 0) throw badRequest("page must be 0 or more");
		if (!ALLOWED_SIZES.contains(size)) throw badRequest("size must be one of 10, 20, 50");
		SearchField searchField = parseField(field);
		String query = q == null || q.isBlank() ? null : q.trim();
		if (query != null && query.length() > MAX_QUERY_LENGTH) {
			throw badRequest("q must be at most " + MAX_QUERY_LENGTH + " characters");
		}
		return boardService.findPage(page, size, searchField, query);
	}

	/** 단건 조회. 기본적으로 호출할 때마다 board_readcount가 1 증가하고, count=false이면 올리지 않는다. */
	@GetMapping("/{id}")
	public Board get(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean count) {
		return boardService.read(id, count);
	}

	/** 원글 작성. 로그인 상태면 이름·비밀번호는 필요 없다. 비회원은 IP당 3분에 1개(초과 시 429). */
	@PostMapping
	public ResponseEntity<?> create(@RequestBody BoardRequest request, Principal principal,
			HttpServletRequest http) {
		Long memberId = CurrentMember.idOf(principal);
		validateCreate(request, memberId);
		String ip = ClientIp.of(http);
		if (memberId == null) {
			long wait = rateLimit.retryAfterSeconds(Kind.POST, ip);
			if (wait > 0) {
				return LimitResponse.tooMany("RATE_LIMITED", wait);
			}
		}
		Board created = boardService.create(request, memberId);
		if (memberId == null) {
			rateLimit.record(Kind.POST, ip);
		}
		return ResponseEntity.created(URI.create("/api/boards/" + created.boardNum())).body(created);
	}

	/** 답글 작성. 비회원은 IP당 1분에 3개(초과 시 429). */
	@PostMapping("/{id}/replies")
	public ResponseEntity<?> reply(@PathVariable Long id, @RequestBody BoardRequest request,
			Principal principal, HttpServletRequest http) {
		Long memberId = CurrentMember.idOf(principal);
		validateCreate(request, memberId);
		String ip = ClientIp.of(http);
		if (memberId == null) {
			long wait = rateLimit.retryAfterSeconds(Kind.REPLY, ip);
			if (wait > 0) {
				return LimitResponse.tooMany("RATE_LIMITED", wait);
			}
		}
		Board created = boardService.reply(id, request, memberId);
		if (memberId == null) {
			rateLimit.record(Kind.REPLY, ip);
		}
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

	private static SearchField parseField(String field) {
		if (field == null || field.isBlank()) return SearchField.TITLE_CONTENT;
		try {
			return SearchField.valueOf(field);
		} catch (IllegalArgumentException e) {
			throw badRequest("field must be TITLE_CONTENT or AUTHOR");
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
