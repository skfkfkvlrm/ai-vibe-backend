package com.oraclejava.pc_mall.order;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancelRepository extends JpaRepository<OrderCancel, Long> {

	List<OrderCancel> findByOrderId(Long orderId);

	List<OrderCancel> findByOrderIdIn(Collection<Long> orderIds);
}
