package com.oraclejava.demoboard.board;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Spring Data JPA 기반 게시판 서비스. 데이터는 MySQL board 테이블에 저장된다. */
@Service
@Transactional(readOnly = true)
public class BoardService {

	private final BoardRepository boardRepository;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public BoardService(BoardRepository boardRepository) {
		this.boardRepository = boardRepository;
	}

	public List<Board> findAll() {
		return boardRepository.findAllByOrderByReRefDescReStepAsc().stream()
				.map(BoardEntity::toBoard)
				.toList();
	}

	/** 단건 조회 시 조회수를 1 증가시키고, 증가된 값을 담아 반환한다. */
	@Transactional
	public Board readAndCount(Long id) {
		if (boardRepository.increaseReadcount(id) == 0) {
			throw notFound(id);
		}
		return boardRepository.findById(id).orElseThrow(() -> notFound(id)).toBoard();
	}

	/** 원글 작성. re_ref는 자신의 글 번호, re_lev와 re_step은 0이다. */
	@Transactional
	public Board create(BoardRequest request) {
		BoardEntity entity = new BoardEntity(request, passwordEncoder.encode(request.boardPass()),
				LocalDateTime.now());
		entity = boardRepository.saveAndFlush(entity);
		entity.assignRoot();
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	/** 답글 작성. 부모 바로 아래에 들어가도록 같은 그룹의 뒤쪽 re_step을 민다. */
	@Transactional
	public Board reply(Long parentId, BoardRequest request) {
		BoardEntity parent = boardRepository.findForUpdate(parentId).orElseThrow(() -> notFound(parentId));
		Long ref = parent.getReRef();
		int lev = parent.getReLev();
		int step = parent.getReStep();

		boardRepository.shiftSteps(ref, step);

		BoardEntity entity = new BoardEntity(request, passwordEncoder.encode(request.boardPass()),
				LocalDateTime.now());
		entity.placeAsReply(ref, lev + 1, step + 1);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public Board update(Long id, BoardRequest request) {
		BoardEntity entity = findAndCheck(id, request.boardPass());
		entity.apply(request);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public Board patch(Long id, BoardRequest request) {
		BoardEntity entity = findAndCheck(id, request.boardPass());
		entity.applyNonNull(request);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public void delete(Long id, String pass) {
		BoardEntity entity = findAndCheck(id, pass);
		boardRepository.delete(entity);
	}

	private BoardEntity findAndCheck(Long id, String pass) {
		BoardEntity entity = boardRepository.findById(id).orElseThrow(() -> notFound(id));
		if (!passwordEncoder.matches(pass, entity.getBoardPass())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password does not match");
		}
		return entity;
	}

	private static ResponseStatusException notFound(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found: " + id);
	}
}
