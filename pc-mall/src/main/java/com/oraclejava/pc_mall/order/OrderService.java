package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.cart.CartService;
import com.oraclejava.pc_mall.member.MemberRepository;
import com.oraclejava.pc_mall.member.MemberRules;

@Service
public class OrderService {

	private static final int MAX_MSG = 500;

	private final OrderRepository orders;
	private final MemberRepository members;
	private final CartService cartService;

	public OrderService(OrderRepository orders, MemberRepository members, CartService cartService) {
		this.orders = orders;
		this.members = members;
		this.cartService = cartService;
	}

	/** 장바구니 내용을 주문(결제 전 ORDERED)으로 만들고 장바구니를 비운다. 합계는 서버가 현재 상품 가격으로 계산한다. */
	@Transactional
	public OrderResponse place(Long memberId, OrderRequest req) {
		if (req == null) {
			throw bad("order form is required");
		}
		String name = MemberRules.name(req.formName());
		String mail = MemberRules.email(req.formMail());
		String msg = req.formMsg() == null ? "" : req.formMsg().trim();
		if (msg.length() > MAX_MSG) {
			throw bad("message too long");
		}
		var items = cartService.items(memberId);
		if (items.isEmpty()) {
			throw bad("cart is empty");
		}
		var order = new Order(members.getReferenceById(memberId), name, mail, msg.isEmpty() ? null : msg,
				LocalDateTime.now());
		items.forEach(c -> order.addLine(new OrderLine(c.getProduct(), c.getQuantity())));
		orders.save(order);
		cartService.clear(memberId);
		return OrderResponse.from(order);
	}

	/** 모의 결제: 실제 PG 호출 없이 상태만 PAID로 바꾼다. 이미 결제된 주문은 그대로 반환한다. */
	@Transactional
	public OrderResponse pay(Long memberId, Long id) {
		var order = find(memberId, id);
		order.markPaid();
		return OrderResponse.from(order);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> list(Long memberId) {
		return orders.findByCustomerMemberIdOrderByIdDesc(memberId).stream().map(OrderResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public OrderResponse get(Long memberId, Long id) {
		return OrderResponse.from(find(memberId, id));
	}

	private Order find(Long memberId, Long id) {
		return orders.findByIdAndCustomerMemberId(id, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
