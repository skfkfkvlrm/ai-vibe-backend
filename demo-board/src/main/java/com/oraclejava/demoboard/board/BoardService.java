package com.oraclejava.demoboard.board;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.demoboard.audit.AuditService;
import com.oraclejava.demoboard.member.MemberAccess;
import com.oraclejava.demoboard.member.MemberService;

/**
 * Spring Data JPA 기반 게시판 서비스. 데이터는 MySQL board 테이블에 저장된다.
 * 수정·삭제 권한: 회원 글은 작성 회원 본인만(비밀번호 불필요), 비회원 글(기존 글 포함)은 글별 비밀번호로 하며,
 * 관리자는 모든 글을 수정·삭제할 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

	private final BoardRepository boardRepository;
	private final MemberService memberService;
	private final MemberAccess memberAccess;
	private final AuditService auditService;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public BoardService(BoardRepository boardRepository, MemberService memberService, MemberAccess memberAccess,
			AuditService auditService) {
		this.boardRepository = boardRepository;
		this.memberService = memberService;
		this.memberAccess = memberAccess;
		this.auditService = auditService;
	}

	public List<Board> findAll() {
		return boardRepository.findAllByDeletedAtIsNullOrderByReRefDescReStepAsc().stream()
				.map(BoardEntity::toBoard)
				.toList();
	}

	/** 단건 조회 시 조회수를 1 증가시키고, 증가된 값을 담아 반환한다. */
	@Transactional
	public Board readAndCount(Long id) {
		if (boardRepository.increaseReadcount(id) == 0) {
			throw notFound(id);
		}
		return boardRepository.findByBoardNumAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id)).toBoard();
	}

	/** 원글 작성. re_ref는 자신의 글 번호, re_lev와 re_step은 0이다. memberId가 있으면 회원 글이다. */
	@Transactional
	public Board create(BoardRequest request, Long memberId) {
		BoardEntity entity = newEntity(request, memberId);
		entity = boardRepository.saveAndFlush(entity);
		entity.assignRoot();
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	/** 답글 작성. 부모 바로 아래에 들어가도록 같은 그룹의 뒤쪽 re_step을 민다. */
	@Transactional
	public Board reply(Long parentId, BoardRequest request, Long memberId) {
		BoardEntity parent = boardRepository.findForUpdate(parentId).orElseThrow(() -> notFound(parentId));
		Long ref = parent.getReRef();
		int lev = parent.getReLev();
		int step = parent.getReStep();

		boardRepository.shiftSteps(ref, step);

		BoardEntity entity = newEntity(request, memberId);
		entity.placeAsReply(ref, lev + 1, step + 1);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public Board update(Long id, BoardRequest request, Long memberId) {
		Checked checked = findAndCheck(id, request.boardPass(), memberId);
		BoardEntity entity = checked.entity();
		// 작성자명은 비회원 글을 비밀번호로 고칠 때만 바꿀 수 있다(회원 글·관리자 수정은 유지).
		boolean nameChangeable = !entity.isOwnedByMember() && !checked.byAdmin();
		if (nameChangeable && (request.boardName() == null || request.boardName().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "board_name is required");
		}
		entity.apply(request, nameChangeable);
		auditAdminEdit(checked, memberId, AuditService.BOARD_EDIT);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public Board patch(Long id, BoardRequest request, Long memberId) {
		Checked checked = findAndCheck(id, request.boardPass(), memberId);
		BoardEntity entity = checked.entity();
		entity.applyNonNull(request, !entity.isOwnedByMember() && !checked.byAdmin());
		auditAdminEdit(checked, memberId, AuditService.BOARD_EDIT);
		return boardRepository.saveAndFlush(entity).toBoard();
	}

	@Transactional
	public void delete(Long id, String pass, Long memberId) {
		Checked checked = findAndCheck(id, pass, memberId);
		BoardEntity entity = checked.entity();
		auditAdminEdit(checked, memberId, AuditService.BOARD_DELETE);
		// 행은 지우지 않고 삭제 시각만 기록한다(soft delete). 답글 행도 그대로 보존된다.
		entity.markDeleted(LocalDateTime.now());
	}

	/** 관리자 권한으로 통과한 수정·삭제만 감사 로그에 남긴다(작성자 본인·비회원 비밀번호 경로는 제외). */
	private void auditAdminEdit(Checked checked, Long adminId, String action) {
		if (checked.byAdmin()) {
			auditService.record(adminId, action, "BOARD", checked.entity().getBoardNum(), null);
		}
	}

	private BoardEntity newEntity(BoardRequest request, Long memberId) {
		LocalDateTime now = LocalDateTime.now();
		if (memberId == null) {
			return new BoardEntity(request, passwordEncoder.encode(request.boardPass()), now);
		}
		String name = memberService.requireName(memberId);
		// 회원 글은 비밀번호 경로를 쓰지 않는다. NOT NULL 컬럼을 채우기 위해 무작위 값의 해시를 저장한다.
		BoardEntity entity = new BoardEntity(request, passwordEncoder.encode(UUID.randomUUID().toString()), now);
		entity.assignMember(memberId, name);
		return entity;
	}

	/** 권한 확인을 통과한 글. byAdmin이면 관리자 권한으로 통과한 것이다(작성자명은 바꾸지 않는다). */
	private record Checked(BoardEntity entity, boolean byAdmin) {
	}

	/**
	 * 수정·삭제 권한 확인.
	 * 1) 회원 글의 작성 회원 본인은 통과(DB 조회 없음).
	 * 2) 로그인한 관리자(DB의 현재 role 기준)는 소유자·비밀번호와 무관하게 통과.
	 * 3) 그 외 회원 글: 비로그인 401, 다른 회원 403.
	 * 4) 비회원 글: 로그인 여부와 무관하게 비밀번호가 없으면 400, 틀리면 403.
	 */
	private Checked findAndCheck(Long id, String pass, Long memberId) {
		BoardEntity entity = boardRepository.findByBoardNumAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id));
		if (entity.isOwnedByMember() && entity.getMemberId().equals(memberId)) {
			return new Checked(entity, false);
		}
		if (memberId != null && memberAccess.isAdmin(memberId)) {
			return new Checked(entity, true);
		}
		if (entity.isOwnedByMember()) {
			if (memberId == null) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
			}
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
		}
		if (pass == null || pass.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "board_pass is required");
		}
		if (!passwordEncoder.matches(pass, entity.getBoardPass())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password does not match");
		}
		return new Checked(entity, false);
	}

	private static ResponseStatusException notFound(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found: " + id);
	}
}
