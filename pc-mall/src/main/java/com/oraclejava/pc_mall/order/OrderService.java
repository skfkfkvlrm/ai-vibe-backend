package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
	private static final int MAX_REASON = 200;

	private final OrderRepository orders;
	private final OrderCancelRepository cancels;
	private final MemberRepository members;
	private final CartService cartService;

	public OrderService(OrderRepository orders, OrderCancelRepository cancels, MemberRepository members,
			CartService cartService) {
		this.orders = orders;
		this.cancels = cancels;
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
		return OrderResponse.from(order, cancels.findByOrderId(order.getId()));
	}

	/** 주문 줄 단위 부분/전체 취소 및 모의 환불. */
	@Transactional
	public OrderResponse cancel(Long memberId, Long id, CancelRequest req) {
		var order = orders.findForCancelByIdAndCustomerMemberId(id, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
		if (Order.STATUS_CANCELED.equals(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "already canceled");
		}
		boolean paid = Order.STATUS_PAID.equals(order.getStatus())
				|| Order.STATUS_PARTIALLY_CANCELED.equals(order.getStatus());
		if (!paid && !Order.STATUS_ORDERED.equals(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot cancel order");
		}
		if (req == null) {
			throw bad("cancel request is required");
		}
		String reason = req.reason() == null ? "" : req.reason().trim();
		if (reason.isEmpty()) {
			throw bad("reason is required");
		}
		if (reason.length() > MAX_REASON) {
			throw bad("reason too long");
		}
		if (req.items() == null || req.items().isEmpty()) {
			throw bad("items are required");
		}

		Map<Long, OrderLine> lineMap = order.getLines().stream()
				.collect(Collectors.toMap(OrderLine::getId, Function.identity()));
		List<OrderCancel> existing = cancels.findByOrderId(order.getId());
		Map<Long, Integer> canceledByLine = new HashMap<>();
		for (OrderCancel c : existing) {
			canceledByLine.merge(c.getOrderLine().getId(), c.getQuantity(), Integer::sum);
		}

		Set<Long> seen = new HashSet<>();
		for (CancelRequest.Item item : req.items()) {
			if (item == null || item.lineId() == null) {
				throw bad("lineId is required");
			}
			if (!seen.add(item.lineId())) {
				throw bad("duplicate lineId");
			}
			OrderLine line = lineMap.get(item.lineId());
			if (line == null) {
				throw bad("order line not found");
			}
			int remaining = line.getQuantity() - canceledByLine.getOrDefault(line.getId(), 0);
			if (item.quantity() < 1 || item.quantity() > remaining) {
				throw bad("invalid cancel quantity");
			}
		}

		List<OrderCancel> allCancels = new ArrayList<>(existing);
		LocalDateTime now = LocalDateTime.now();
		for (CancelRequest.Item item : req.items()) {
			OrderLine line = lineMap.get(item.lineId());
			int unitPrice = paid ? line.getUnitPrice() : 0;
			int refund = unitPrice * item.quantity();
			allCancels.add(cancels.save(new OrderCancel(order, line, item.quantity(), refund, reason, now)));
			canceledByLine.merge(line.getId(), item.quantity(), Integer::sum);
		}

		boolean allCanceled = order.getLines().stream()
				.allMatch(l -> canceledByLine.getOrDefault(l.getId(), 0) >= l.getQuantity());
		if (allCanceled) {
			order.markCanceled();
		} else if (paid) {
			order.markPartiallyCanceled();
		}
		return OrderResponse.from(order, allCancels);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> list(Long memberId) {
		List<Order> list = orders.findByCustomerMemberIdOrderByIdDesc(memberId);
		if (list.isEmpty()) {
			return List.of();
		}
		List<Long> orderIds = list.stream().map(Order::getId).toList();
		Map<Long, List<OrderCancel>> cancelsByOrder = cancels.findByOrderIdIn(orderIds).stream()
				.collect(Collectors.groupingBy(c -> c.getOrder().getId()));
		return list.stream()
				.map(o -> OrderResponse.from(o, cancelsByOrder.getOrDefault(o.getId(), List.of())))
				.toList();
	}

	@Transactional(readOnly = true)
	public OrderResponse get(Long memberId, Long id) {
		var order = find(memberId, id);
		return OrderResponse.from(order, cancels.findByOrderId(order.getId()));
	}

	private Order find(Long memberId, Long id) {
		return orders.findByIdAndCustomerMemberId(id, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
