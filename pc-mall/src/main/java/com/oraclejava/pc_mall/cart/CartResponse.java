package com.oraclejava.pc_mall.cart;

import java.util.List;

public record CartResponse(List<CartItemResponse> items, int totalPrice) {

	public static CartResponse of(List<CartItemResponse> items) {
		return new CartResponse(items, items.stream().mapToInt(CartItemResponse::subtotal).sum());
	}
}
