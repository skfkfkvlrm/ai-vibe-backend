package com.oraclejava.pc_mall.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {

	Optional<LoginAttemptEntity> findByLoginIdAndIp(String loginId, String ip);
}
