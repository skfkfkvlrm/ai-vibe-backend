package com.oraclejava.demoboard.security;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WriteRateLimitRepository extends JpaRepository<WriteRateLimitEntity, Long> {

	/** cutoff 이전의 기록을 지운다(제한 창은 최대 3분이라 1일 지난 기록은 판정에 쓰이지 않는다). */
	@Modifying
	@Query("delete from WriteRateLimitEntity w where w.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);


	List<WriteRateLimitEntity> findByIpAndKindAndCreatedAtAfterOrderByCreatedAtAsc(String ip, String kind,
			LocalDateTime after);
}
