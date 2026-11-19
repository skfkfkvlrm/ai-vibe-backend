package com.oraclejava.demoboard.board;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** board 테이블에 매핑되는 JPA 엔티티. API 응답은 {@link Board} 레코드로 변환해 반환한다. */
@Entity
@Table(name = "board")
public class BoardEntity {

	/** 글 번호 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "board_num")
	private Long boardNum;

	/** 작성자 */
	@Column(name = "board_name", nullable = false, length = 100)
	private String boardName;

	/** 글 비밀번호 (BCrypt 해시) */
	@Column(name = "board_pass", nullable = false, length = 100)
	private String boardPass;

	/** 글 제목 */
	@Column(name = "board_subject", nullable = false, length = 300)
	private String boardSubject;

	/** 글 내용 */
	@Lob
	@Column(name = "board_content", columnDefinition = "TEXT")
	private String boardContent;

	/** 첨부파일 (파일명 문자열) */
	@Column(name = "board_file", length = 500)
	private String boardFile;

	/** 관련 글 번호 (원글은 자기 자신의 board_num) */
	@Column(name = "re_ref", nullable = false)
	private Long reRef;

	/** 답글 레벨 (원글 0) */
	@Column(name = "re_lev", nullable = false)
	private int reLev;

	/** 같은 re_ref 안에서의 출력 순서 (원글 0) */
	@Column(name = "re_step", nullable = false)
	private int reStep;

	/** 조회수 */
	@Column(name = "board_readcount", nullable = false)
	private long boardReadcount;

	/** 작성일 */
	@Column(name = "board_date", nullable = false)
	private LocalDateTime boardDate;

	/** 삭제 시각. null이면 삭제되지 않은 글 (soft delete) */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected BoardEntity() {
	}

	/** 새 글. re_ref는 저장 후 {@link #assignRoot()} 또는 {@link #placeAsReply}로 확정한다. */
	public BoardEntity(BoardRequest r, String passHash, LocalDateTime now) {
		this.boardName = r.boardName();
		this.boardPass = passHash;
		this.boardSubject = r.boardSubject();
		this.boardContent = r.boardContent();
		this.boardFile = r.boardFile();
		this.reRef = 0L;
		this.boardDate = now;
	}

	/** 원글: re_ref를 자신의 글 번호로 한다. */
	public void assignRoot() {
		this.reRef = this.boardNum;
		this.reLev = 0;
		this.reStep = 0;
	}

	/** 답글 위치를 지정한다. */
	public void placeAsReply(Long reRef, int reLev, int reStep) {
		this.reRef = reRef;
		this.reLev = reLev;
		this.reStep = reStep;
	}

	/** 요청의 모든 필드로 덮어쓴다 (PUT). 비밀번호는 바꾸지 않는다. */
	public void apply(BoardRequest r) {
		this.boardName = r.boardName();
		this.boardSubject = r.boardSubject();
		this.boardContent = r.boardContent();
		this.boardFile = r.boardFile();
	}

	/** 요청에서 null이 아닌 필드만 반영한다 (PATCH). 비밀번호는 바꾸지 않는다. */
	public void applyNonNull(BoardRequest r) {
		if (r.boardName() != null) this.boardName = r.boardName();
		if (r.boardSubject() != null) this.boardSubject = r.boardSubject();
		if (r.boardContent() != null) this.boardContent = r.boardContent();
		if (r.boardFile() != null) this.boardFile = r.boardFile();
	}

	/** 행을 지우지 않고 삭제 시각만 기록한다. */
	public void markDeleted(LocalDateTime now) {
		this.deletedAt = now;
	}

	public Board toBoard() {
		return new Board(boardNum, boardName, boardSubject, boardContent, boardFile,
				reRef, reLev, reStep, boardReadcount, boardDate);
	}

	public Long getBoardNum() {
		return boardNum;
	}

	public String getBoardPass() {
		return boardPass;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public Long getReRef() {
		return reRef;
	}

	public int getReLev() {
		return reLev;
	}

	public int getReStep() {
		return reStep;
	}
}
