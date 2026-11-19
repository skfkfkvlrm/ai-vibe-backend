package com.oraclejava.demoboard.security;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {

	Optional<LoginAttemptEntity> findByLoginIdAndIp(String loginId, String ip);

	/**
	 * cutoff 이전에 마지막으로 실패한 행을 지운다. last_failed_at이 없는 예전 행은 잠금이 cutoff 전에 풀렸거나
	 * 잠기지 않았을 때만 지운다(잠금 중인 행은 남긴다).
	 */
	@Modifying
	@Query("delete from LoginAttemptEntity a where a.lastFailedAt < :cutoff "
			+ "or (a.lastFailedAt is null and (a.lockedUntil is null or a.lockedUntil < :cutoff))")
	int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
