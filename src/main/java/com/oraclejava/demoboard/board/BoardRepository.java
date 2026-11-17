package com.oraclejava.demoboard.board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

	/** 원글 그룹은 최신순, 그룹 안에서는 re_step 순(원글 아래에 답글). */
	List<BoardEntity> findAllByOrderByReRefDescReStepAsc();

	/** 동시 요청에서도 누락되지 않도록 DB에서 원자적으로 조회수를 1 올린다. */
	@Modifying(clearAutomatically = true)
	@Query("update BoardEntity b set b.boardReadcount = b.boardReadcount + 1 where b.boardNum = :id")
	int increaseReadcount(@Param("id") Long id);

	/** 답글이 들어갈 자리를 만들기 위해 같은 그룹에서 step보다 큰 re_step을 1씩 민다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update BoardEntity b set b.reStep = b.reStep + 1 where b.reRef = :ref and b.reStep > :step")
	int shiftSteps(@Param("ref") Long ref, @Param("step") int step);

	/** 동시 답글의 re_step 충돌을 막기 위해 부모 행을 잠그고 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from BoardEntity b where b.boardNum = :id")
	Optional<BoardEntity> findForUpdate(@Param("id") Long id);
}
