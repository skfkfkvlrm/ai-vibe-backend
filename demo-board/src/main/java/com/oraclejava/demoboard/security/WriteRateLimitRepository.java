package com.oraclejava.demoboard.security;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WriteRateLimitRepository extends JpaRepository<WriteRateLimitEntity, Long> {

	List<WriteRateLimitEntity> findByIpAndKindAndCreatedAtAfterOrderByCreatedAtAsc(String ip, String kind,
			LocalDateTime after);
}
