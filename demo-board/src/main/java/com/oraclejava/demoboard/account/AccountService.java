package com.oraclejava.demoboard.account;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oraclejava.demoboard.board.Board;
import com.oraclejava.demoboard.board.BoardEntity;
import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberService;

/**
 * 회원 본인 기능 중 회원과 게시판 두 도메인을 함께 바꾸는 작업(이름 변경, 탈퇴)과 내가 쓴 글 조회.
 * 게시판 서비스와 회원 서비스가 서로 의존하지 않도록 이 계층에서만 둘을 엮는다.
 */
@Service
@Transactional
public class AccountService {

	private final MemberService memberService;
	private final BoardRepository boardRepository;

	public AccountService(MemberService memberService, BoardRepository boardRepository) {
		this.memberService = memberService;
		this.boardRepository = boardRepository;
	}

	/** 이름이 바뀌면 그 회원의 모든 글(삭제된 글 포함)의 작성자명도 같은 트랜잭션에서 갱신한다. */
	public MemberEntity updateProfile(Long memberId, String newName, String newEmail) {
		String oldName = memberService.requireName(memberId);
		MemberEntity updated = memberService.updateProfile(memberId, newName, newEmail);
		if (newName != null && !newName.equals(oldName)) {
			boardRepository.updateAuthorName(memberId, newName);
		}
		return updated;
	}

	/** 개인정보를 익명화하고, 작성한 글은 작성자명만 '탈퇴한 회원'으로 바꿔 남긴다. */
	public void withdraw(Long memberId, String password) {
		memberService.withdraw(memberId, password);
		boardRepository.updateAuthorName(memberId, MemberEntity.WITHDRAWN_NAME);
	}

	@Transactional(readOnly = true)
	public List<Board> myPosts(Long memberId) {
		return boardRepository.findAllByMemberIdAndDeletedAtIsNullOrderByBoardNumDesc(memberId).stream()
				.map(BoardEntity::toBoard)
				.toList();
	}
}
