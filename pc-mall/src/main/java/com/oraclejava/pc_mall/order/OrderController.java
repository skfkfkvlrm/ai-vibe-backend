package com.oraclejava.pc_mall.order;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.security.CurrentMember;

/** 주문 API. 로그인 회원 전용이며 본인 주문만 다룬다. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse place(@RequestBody OrderRequest request, Principal principal) {
		return service.place(member(principal), request);
	}

	@GetMapping
	public List<OrderResponse> list(Principal principal) {
		return service.list(member(principal));
	}

	@GetMapping("/{id}")
	public OrderResponse get(@PathVariable Long id, Principal principal) {
		return service.get(member(principal), id);
	}

	/** 모의 결제(PG 연동 없음). */
	@PostMapping("/{id}/pay")
	public OrderResponse pay(@PathVariable Long id, Principal principal) {
		return service.pay(member(principal), id);
	}

	/** 주문 부분/전체 취소(모의 환불). */
	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@PathVariable Long id, @RequestBody CancelRequest request, Principal principal) {
		return service.cancel(member(principal), id, request);
	}

	private static Long member(Principal principal) {
		Long id = CurrentMember.idOf(principal);
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return id;
	}
}
