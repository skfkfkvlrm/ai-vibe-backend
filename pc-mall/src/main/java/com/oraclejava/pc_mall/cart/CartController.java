package com.oraclejava.pc_mall.cart;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.security.CurrentMember;

/** 장바구니 API. 로그인 회원 전용(SecurityConfig의 그 외 경로 규칙). */
@RestController
@RequestMapping("/api/cart")
public class CartController {

	private final CartService service;

	public CartController(CartService service) {
		this.service = service;
	}

	@GetMapping
	public CartResponse get(Principal principal) {
		return service.get(member(principal));
	}

	@PostMapping
	public CartResponse add(@RequestBody CartRequest request, Principal principal) {
		Long memberId = member(principal);
		if (request == null || request.quantity() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId and quantity are required");
		}
		return service.add(memberId, request.productId(), request.quantity());
	}

	@PatchMapping("/{id}")
	public CartResponse changeQuantity(@PathVariable Long id, @RequestBody CartRequest request,
			Principal principal) {
		Long memberId = member(principal);
		if (request == null || request.quantity() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity is required");
		}
		return service.changeQuantity(memberId, id, request.quantity());
	}

	@DeleteMapping("/{id}")
	public CartResponse remove(@PathVariable Long id, Principal principal) {
		return service.remove(member(principal), id);
	}

	private static Long member(Principal principal) {
		Long id = CurrentMember.idOf(principal);
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return id;
	}
}
