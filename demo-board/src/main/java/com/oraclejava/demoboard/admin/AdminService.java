package com.oraclejava.demoboard.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.demoboard.board.Board;
import com.oraclejava.demoboard.board.BoardEntity;
import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 관리자 기능. 호출 권한은 AdminController가 검사한다. */
@Service
@Transactional(readOnly = true)
public class AdminService {

	private static final int MAX_REASON_LENGTH = 500;

	private final BoardRepository boardRepository;
	private final MemberService memberService;

	public AdminService(BoardRepository boardRepository, MemberService memberService) {
		this.boardRepository = boardRepository;
		this.memberService = memberService;
	}

	/** 삭제된 글·답글을 삭제 시각 역순으로. 답글에는 원글의 삭제 여부를 함께 담는다. */
	public List<DeletedBoardResponse> deletedBoards() {
		List<BoardEntity> deleted = boardRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();
		Set<Long> rootIds = deleted.stream()
				.filter(b -> b.getReLev() > 0)
				.map(BoardEntity::getReRef)
				.collect(Collectors.toSet());
		Map<Long, BoardEntity> roots = boardRepository.findAllById(rootIds).stream()
				.collect(Collectors.toMap(BoardEntity::getBoardNum, Function.identity()));

		return deleted.stream().map(b -> {
			BoardEntity root = b.getReLev() > 0 ? roots.get(b.getReRef()) : null;
			boolean rootDeleted = root != null && root.getDeletedAt() != null;
			return new DeletedBoardResponse(b.getBoardNum(), b.getBoardName(), b.getBoardSubject(),
					b.getBoardContent(), b.getReRef(), b.getReLev(), b.getMemberId(), b.getBoardDate(),
					b.getDeletedAt(), rootDeleted);
		}).toList();
	}

	/** 선택한 글 1건만 복구한다. 삭제 상태가 아니거나 없는 글이면 404. */
	@Transactional
	public Board restore(Long id) {
		BoardEntity entity = boardRepository.findById(id)
				.filter(b -> b.getDeletedAt() != null)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted board not found: " + id));
		entity.restore();
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	public List<AdminMemberResponse> members() {
		LocalDateTime now = LocalDateTime.now();
		return memberService.findAll().stream().map(m -> AdminMemberResponse.from(m, now)).toList();
	}

	/** 영구(PERMANENT)·기간(1D/7D/30D)·직접 입력(UNTIL) 밴. 사유는 필수. */
	@Transactional
	public AdminMemberResponse ban(Long adminId, Long targetId, BanRequest request) {
		if (request == null || request.reason() == null || request.reason().isBlank()
				|| request.reason().trim().length() > MAX_REASON_LENGTH) {
			throw badRequest("reason is required (max 500 chars)");
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime until = switch (request.type() == null ? "" : request.type()) {
			case "1D" -> now.plusDays(1);
			case "7D" -> now.plusDays(7);
			case "30D" -> now.plusDays(30);
			case "PERMANENT" -> null;
			case "UNTIL" -> {
				if (request.until() == null || !request.until().isAfter(now)) {
					throw badRequest("until must be a future date-time");
				}
				yield request.until();
			}
			default -> throw badRequest("type must be one of 1D, 7D, 30D, PERMANENT, UNTIL");
		};
		return AdminMemberResponse.from(memberService.ban(adminId, targetId, request.reason().trim(), until), now);
	}

	@Transactional
	public AdminMemberResponse unban(Long targetId) {
		return AdminMemberResponse.from(memberService.unban(targetId), LocalDateTime.now());
	}

	@Transactional
	public TempPasswordResponse resetPassword(Long adminId, Long targetId) {
		return new TempPasswordResponse(memberService.resetPassword(adminId, targetId));
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
