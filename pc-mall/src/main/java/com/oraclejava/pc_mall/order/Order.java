package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.oraclejava.pc_mall.member.MemberEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** 주문 정보. order는 SQL 예약어라 테이블명은 orders, 엔티티명은 PcOrder로 둔다. */
@Entity(name = "PcOrder")
@Table(name = "orders")
public class Order {

	public static final String STATUS_ORDERED = "ORDERED";
	/** 모의 결제 완료. 실제 PG 연동은 없다. */
	public static final String STATUS_PAID = "PAID";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private MemberEntity customer;

	@Column(name = "order_date", nullable = false)
	private LocalDateTime orderDate;

	@Column(name = "total_price", nullable = false)
	private int totalPrice;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderLine> lines = new ArrayList<>();

	@Column(name = "form_name", nullable = false, length = 100)
	private String formName;

	@Column(name = "form_mail", nullable = false, length = 200)
	private String formMail;

	@Column(name = "form_msg", length = 500)
	private String formMsg;

	@Column(nullable = false, length = 20)
	private String status;

	protected Order() {
	}

	public Order(MemberEntity customer, String formName, String formMail, String formMsg, LocalDateTime now) {
		this.customer = customer;
		this.formName = formName;
		this.formMail = formMail;
		this.formMsg = formMsg;
		this.orderDate = now;
		this.status = STATUS_ORDERED;
	}

	public void addLine(OrderLine line) {
		lines.add(line);
		line.setOrder(this);
		totalPrice += line.getProduct().getPrice() * line.getQuantity();
	}

	public void markPaid() {
		this.status = STATUS_PAID;
	}

	public Long getId() {
		return id;
	}

	public MemberEntity getCustomer() {
		return customer;
	}

	public LocalDateTime getOrderDate() {
		return orderDate;
	}

	public int getTotalPrice() {
		return totalPrice;
	}

	public List<OrderLine> getLines() {
		return lines;
	}

	public String getFormName() {
		return formName;
	}

	public String getFormMail() {
		return formMail;
	}

	public String getFormMsg() {
		return formMsg;
	}

	public String getStatus() {
		return status;
	}
}
