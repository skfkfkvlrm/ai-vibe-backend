package com.oraclejava.pc_mall.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = { "lines", "lines.product" })
	List<Order> findByCustomerMemberIdOrderByIdDesc(Long memberId);

	@EntityGraph(attributePaths = { "lines", "lines.product" })
	Optional<Order> findByIdAndCustomerMemberId(Long id, Long memberId);

	/** 취소 동시성 제어를 위한 쓰기 잠금 조회. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "lines", "lines.product" })
	@Query("select o from PcOrder o where o.id = :id and o.customer.memberId = :memberId")
	Optional<Order> findForCancelByIdAndCustomerMemberId(@Param("id") Long id, @Param("memberId") Long memberId);
}
