package com.oraclejava.pc_mall.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = { "lines", "lines.product" })
	List<Order> findByCustomerMemberIdOrderByIdDesc(Long memberId);

	@EntityGraph(attributePaths = { "lines", "lines.product" })
	Optional<Order> findByIdAndCustomerMemberId(Long id, Long memberId);
}
