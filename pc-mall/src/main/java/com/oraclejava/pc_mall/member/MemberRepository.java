package com.oraclejava.pc_mall.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

	Optional<MemberEntity> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);

	boolean existsByEmail(String email);

	List<MemberEntity> findAllByOrderByMemberIdAsc();

	/** 해당 역할의 행을 쓰기 잠금으로 읽는다. 마지막 ADMIN 해제 판정을 동시 요청 사이에서 직렬화하려고 쓴다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<MemberEntity> findAllByRole(String role);
}
