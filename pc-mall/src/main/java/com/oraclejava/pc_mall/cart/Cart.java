package com.oraclejava.pc_mall.cart;

import com.oraclejava.pc_mall.member.MemberEntity;
import com.oraclejava.pc_mall.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 장바구니 한 줄(회원 + 상품 + 수량). 같은 회원·상품은 한 행으로 합쳐진다. */
@Entity
@Table(name = "cart", uniqueConstraints = @UniqueConstraint(name = "uk_cart_member_product", columnNames = {
		"member_id", "product_id" }))
public class Cart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private MemberEntity member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int quantity;

	protected Cart() {
	}

	public Cart(MemberEntity member, Product product, int quantity) {
		this.member = member;
		this.product = product;
		this.quantity = quantity;
	}

	public void changeQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public MemberEntity getMember() {
		return member;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
