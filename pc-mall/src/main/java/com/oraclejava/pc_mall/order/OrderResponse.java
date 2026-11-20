package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OrderResponse(Long id, LocalDateTime orderDate, int totalPrice, String status, String formName,
		String formMail, String formMsg, List<Line> lines, int refundedTotal) {

	/** 상품이 이후 삭제됐으면 name은 "(삭제된 상품)", price는 0. */
	public record Line(Long lineId, Long productId, String name, int price, int quantity, int canceledQuantity) {
	}

	public static OrderResponse from(Order o) {
		return from(o, Map.of(), 0);
	}

	public static OrderResponse from(Order o, Map<Long, Integer> canceledByLine, int refundedTotal) {
		var lines = o.getLines().stream().map(l -> {
			var p = l.getProduct();
			int canceled = canceledByLine.getOrDefault(l.getId(), 0);
			return new Line(l.getId(), p == null ? null : p.getId(), p == null ? "(삭제된 상품)" : p.getName(),
					p == null ? 0 : p.getPrice(), l.getQuantity(), canceled);
		}).toList();
		return new OrderResponse(o.getId(), o.getOrderDate(), o.getTotalPrice(), o.getStatus(), o.getFormName(),
				o.getFormMail(), o.getFormMsg(), lines, refundedTotal);
	}
}
