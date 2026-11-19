package com.oraclejava.demoboard.admin;

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
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberResponse;

/** 관리자 기능. 호출 권한은 AdminController가 검사한다. */
@Service
@Transactional(readOnly = true)
public class AdminService {

	private final BoardRepository boardRepository;
	private final MemberRepository memberRepository;

	public AdminService(BoardRepository boardRepository, MemberRepository memberRepository) {
		this.boardRepository = boardRepository;
		this.memberRepository = memberRepository;
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

	public List<MemberResponse> members() {
		return memberRepository.findAllByOrderByMemberIdDesc().stream().map(MemberEntity::toResponse).toList();
	}
}
