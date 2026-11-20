package com.oraclejava.pc_mall.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

	@EntityGraph(attributePaths = "product")
	List<Cart> findByMemberMemberIdOrderByIdAsc(Long memberId);

	Optional<Cart> findByMemberMemberIdAndProductId(Long memberId, Long productId);

	Optional<Cart> findByIdAndMemberMemberId(Long id, Long memberId);

	void deleteByMemberMemberId(Long memberId);
}
