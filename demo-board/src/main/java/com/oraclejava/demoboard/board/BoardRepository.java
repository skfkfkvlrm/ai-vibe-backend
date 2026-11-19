package com.oraclejava.demoboard.board;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

	/** 검색 조건은 LIKE ... ESCAPE '!' 로 비교한다(와일드카드 이스케이프는 BoardService). */
	String ROOT = "b.reLev = 0 and b.deletedAt is null";
	// 본문(TEXT)은 CLOB로 매핑되어 lower()에 바로 넣을 수 없으므로 문자열로 cast한다.
	String TITLE_CONTENT_OF_B = "(lower(b.boardSubject) like :p escape '!' "
			+ "or lower(cast(b.boardContent as string)) like :p escape '!')";
	String TITLE_CONTENT_OF_R = "(lower(r.boardSubject) like :p escape '!' "
			+ "or lower(cast(r.boardContent as string)) like :p escape '!')";
	String AUTHOR_OF_B = "lower(b.boardName) like :p escape '!'";
	String AUTHOR_OF_R = "lower(r.boardName) like :p escape '!'";
	/** 삭제되지 않은 답글 중 조건에 맞는 것이 있는 원글도 결과에 포함한다. */
	String REPLY_EXISTS = "exists (select 1 from BoardEntity r where r.reRef = b.reRef and r.reLev > 0 "
			+ "and r.deletedAt is null and ";

	/** 삭제되지 않은 원글, 최신 원글 먼저. */
	@Query(value = "select b from BoardEntity b where " + ROOT + " order by b.reRef desc",
			countQuery = "select count(b) from BoardEntity b where " + ROOT)
	Page<BoardEntity> findRoots(Pageable pageable);

	/** 원글 또는 그 답글의 제목·내용에 검색어(pattern)가 있는 원글. */
	@Query(value = "select b from BoardEntity b where " + ROOT + " and (" + TITLE_CONTENT_OF_B + " or "
			+ REPLY_EXISTS + TITLE_CONTENT_OF_R + ")) order by b.reRef desc",
			countQuery = "select count(b) from BoardEntity b where " + ROOT + " and (" + TITLE_CONTENT_OF_B + " or "
					+ REPLY_EXISTS + TITLE_CONTENT_OF_R + "))")
	Page<BoardEntity> searchRootsByTitleContent(@Param("p") String pattern, Pageable pageable);

	/** 원글 또는 그 답글의 작성자명에 검색어(pattern)가 있는 원글. */
	@Query(value = "select b from BoardEntity b where " + ROOT + " and (" + AUTHOR_OF_B + " or "
			+ REPLY_EXISTS + AUTHOR_OF_R + ")) order by b.reRef desc",
			countQuery = "select count(b) from BoardEntity b where " + ROOT + " and (" + AUTHOR_OF_B + " or "
					+ REPLY_EXISTS + AUTHOR_OF_R + "))")
	Page<BoardEntity> searchRootsByAuthor(@Param("p") String pattern, Pageable pageable);

	/** 주어진 원글들의 삭제되지 않은 답글. 원글 최신순, 그룹 안에서는 re_step 순. */
	@Query("select b from BoardEntity b where b.reRef in :roots and b.reLev > 0 and b.deletedAt is null "
			+ "order by b.reRef desc, b.reStep asc")
	List<BoardEntity> findRepliesOf(@Param("roots") Collection<Long> roots);

	/** 회원이 쓴 삭제되지 않은 글·답글, 최신 글 먼저. */
	List<BoardEntity> findAllByMemberIdAndDeletedAtIsNullOrderByBoardNumDesc(Long memberId);

	/** 회원 이름이 바뀌거나 탈퇴할 때 그 회원이 쓴 모든 글(삭제된 글 포함)의 작성자명을 일괄 갱신한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update BoardEntity b set b.boardName = :name where b.memberId = :memberId")
	int updateAuthorName(@Param("memberId") Long memberId, @Param("name") String name);

	/** 관리자용: 삭제된 글·답글을 삭제 시각 역순으로. */
	List<BoardEntity> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

	/** 삭제되지 않은 글 한 건. 삭제된 글은 없는 글로 취급한다. */
	Optional<BoardEntity> findByBoardNumAndDeletedAtIsNull(Long boardNum);

	/** 동시 요청에서도 누락되지 않도록 DB에서 원자적으로 조회수를 1 올린다. 삭제된 글은 제외. */
	@Modifying(clearAutomatically = true)
	@Query("update BoardEntity b set b.boardReadcount = b.boardReadcount + 1 "
			+ "where b.boardNum = :id and b.deletedAt is null")
	int increaseReadcount(@Param("id") Long id);

	/** 답글이 들어갈 자리를 만들기 위해 같은 그룹에서 step보다 큰 re_step을 1씩 민다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update BoardEntity b set b.reStep = b.reStep + 1 where b.reRef = :ref and b.reStep > :step")
	int shiftSteps(@Param("ref") Long ref, @Param("step") int step);

	/** 동시 답글의 re_step 충돌을 막기 위해 부모 행을 잠그고 조회한다. 삭제된 글은 제외. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BoardEntity b where b.boardNum = :id and b.deletedAt is null")
	Optional<BoardEntity> findForUpdate(@Param("id") Long id);
}
