package com.oraclejava.pc_mall.cart;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.oraclejava.pc_mall.member.MemberRepository;
import com.oraclejava.pc_mall.product.ProductRepository;

@Service
public class CartService {

	public static final int MAX_QUANTITY = 99;

	private final CartRepository carts;
	private final MemberRepository members;
	private final ProductRepository products;

	public CartService(CartRepository carts, MemberRepository members, ProductRepository products) {
		this.carts = carts;
		this.members = members;
		this.products = products;
	}

	@Transactional(readOnly = true)
	public CartResponse get(Long memberId) {
		return CartResponse.of(items(memberId).stream().map(CartItemResponse::from).toList());
	}

	/** 삭제된 상품(product=null)이 담겨 있으면 제외한 유효한 장바구니 행. 주문 생성에서도 쓴다. */
	@Transactional(readOnly = true)
	public List<Cart> items(Long memberId) {
		return carts.findByMemberMemberIdOrderByIdAsc(memberId).stream().filter(c -> c.getProduct() != null)
				.toList();
	}

	@Transactional
	public CartResponse add(Long memberId, Long productId, int quantity) {
		checkQuantity(quantity);
		if (productId == null) {
			throw bad("productId is required");
		}
		var product = products.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
		var existing = carts.findByMemberMemberIdAndProductId(memberId, productId);
		if (existing.isPresent()) {
			existing.get().changeQuantity(Math.min(MAX_QUANTITY, existing.get().getQuantity() + quantity));
		} else {
			carts.save(new Cart(members.getReferenceById(memberId), product, quantity));
		}
		carts.flush();
		return get(memberId);
	}

	@Transactional
	public CartResponse changeQuantity(Long memberId, Long id, int quantity) {
		checkQuantity(quantity);
		carts.findByIdAndMemberMemberId(id, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found"))
				.changeQuantity(quantity);
		carts.flush();
		return get(memberId);
	}

	@Transactional
	public CartResponse remove(Long memberId, Long id) {
		carts.delete(carts.findByIdAndMemberMemberId(id, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found")));
		carts.flush();
		return get(memberId);
	}

	@Transactional
	public void clear(Long memberId) {
		carts.deleteByMemberMemberId(memberId);
	}

	private static void checkQuantity(int quantity) {
		if (quantity < 1 || quantity > MAX_QUANTITY) {
			throw bad("quantity must be 1.." + MAX_QUANTITY);
		}
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
