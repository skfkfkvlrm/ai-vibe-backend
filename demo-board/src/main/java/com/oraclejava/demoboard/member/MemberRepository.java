package com.oraclejava.demoboard.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

	Optional<MemberEntity> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);

	boolean existsByEmail(String email);
}
