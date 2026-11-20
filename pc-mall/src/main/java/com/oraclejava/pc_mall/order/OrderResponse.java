package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, LocalDateTime orderDate, int totalPrice, String status, String formName,
		String formMail, String formMsg, List<Line> lines) {

	/** 상품이 이후 삭제됐으면 name은 "(삭제된 상품)", price는 0. */
	public record Line(Long productId, String name, int price, int quantity) {
	}

	public static OrderResponse from(Order o) {
		var lines = o.getLines().stream().map(l -> {
			var p = l.getProduct();
			return new Line(p == null ? null : p.getId(), p == null ? "(삭제된 상품)" : p.getName(),
					p == null ? 0 : p.getPrice(), l.getQuantity());
		}).toList();
		return new OrderResponse(o.getId(), o.getOrderDate(), o.getTotalPrice(), o.getStatus(), o.getFormName(),
				o.getFormMail(), o.getFormMsg(), lines);
	}
}
