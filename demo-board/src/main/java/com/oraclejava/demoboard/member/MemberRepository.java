package com.oraclejava.demoboard.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

	Optional<MemberEntity> findByLoginId(String loginId);

	/** 관리자용: 가입 최신순 전체. */
	List<MemberEntity> findAllByOrderByMemberIdDesc();

	boolean existsByLoginId(String loginId);

	boolean existsByEmail(String email);
}
