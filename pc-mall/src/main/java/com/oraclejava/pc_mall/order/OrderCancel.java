package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 주문 줄 단위 취소 및 모의 환불 이력. */
@Entity
@Table(name = "order_cancel")
public class OrderCancel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_line_id", nullable = false)
	private OrderLine orderLine;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "refund_amount", nullable = false)
	private int refundAmount;

	@Column(nullable = false, length = 200)
	private String reason;

	@Column(name = "canceled_at", nullable = false)
	private LocalDateTime canceledAt;

	protected OrderCancel() {
	}

	public OrderCancel(Order order, OrderLine orderLine, int quantity, int refundAmount, String reason,
			LocalDateTime canceledAt) {
		this.order = order;
		this.orderLine = orderLine;
		this.quantity = quantity;
		this.refundAmount = refundAmount;
		this.reason = reason;
		this.canceledAt = canceledAt;
	}

	public Long getId() {
		return id;
	}

	public Order getOrder() {
		return order;
	}

	public OrderLine getOrderLine() {
		return orderLine;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getRefundAmount() {
		return refundAmount;
	}

	public String getReason() {
		return reason;
	}

	public LocalDateTime getCanceledAt() {
		return canceledAt;
	}
}
