package com.oraclejava.pc_mall.cart;

public record CartItemResponse(Long id, Long productId, String name, int price, boolean hasPicS, int quantity,
		int subtotal) {

	public static CartItemResponse from(Cart c) {
		return new CartItemResponse(c.getId(), c.getProduct().getId(), c.getProduct().getName(),
				c.getProduct().getPrice(), c.getProduct().getPicS() != null, c.getQuantity(),
				c.getProduct().getPrice() * c.getQuantity());
	}
}
