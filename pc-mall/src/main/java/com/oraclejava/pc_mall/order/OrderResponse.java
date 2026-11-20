package com.oraclejava.pc_mall.order;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record OrderResponse(Long id, LocalDateTime orderDate, int totalPrice, String status, String formName,
		String formMail, String formMsg, List<Line> lines, int refundedTotal, int canceledAmount, int payableAmount,
		List<Cancel> cancels) {

	/** 상품이 이후 삭제됐으면 name은 "(삭제된 상품)", price는 주문 시점 단가 스냅샷(없으면 0). */
	public record Line(Long lineId, Long productId, String name, int price, int quantity, int canceledQuantity) {
	}

	public record Cancel(String productName, int quantity, int amount, int refundAmount, String reason,
			LocalDateTime canceledAt) {
	}

	public static OrderResponse from(Order o) {
		return from(o, List.of());
	}

	public static OrderResponse from(Order o, List<OrderCancel> orderCancels) {
		Map<Long, Integer> canceledByLine = new HashMap<>();
		int refundedTotal = 0;
		int canceledAmount = 0;
		for (OrderCancel c : orderCancels) {
			OrderLine line = c.getOrderLine();
			canceledByLine.merge(line.getId(), c.getQuantity(), Integer::sum);
			refundedTotal += c.getRefundAmount();
			canceledAmount += c.getQuantity() * line.getUnitPrice();
		}
		var lines = o.getLines().stream().map(l -> {
			var p = l.getProduct();
			int canceled = canceledByLine.getOrDefault(l.getId(), 0);
			return new Line(l.getId(), p == null ? null : p.getId(), p == null ? "(삭제된 상품)" : p.getName(),
					l.getUnitPrice(), l.getQuantity(), canceled);
		}).toList();
		var cancels = orderCancels.stream()
				.sorted(Comparator.comparing(OrderCancel::getCanceledAt))
				.map(c -> {
					OrderLine line = c.getOrderLine();
					var p = line.getProduct();
					String productName = p == null ? "(삭제된 상품)" : p.getName();
					int amount = c.getQuantity() * line.getUnitPrice();
					return new Cancel(productName, c.getQuantity(), amount, c.getRefundAmount(), c.getReason(),
							c.getCanceledAt());
				}).toList();
		int payableAmount = o.getTotalPrice() - canceledAmount;
		return new OrderResponse(o.getId(), o.getOrderDate(), o.getTotalPrice(), o.getStatus(), o.getFormName(),
				o.getFormMail(), o.getFormMsg(), lines, refundedTotal, canceledAmount, payableAmount, cancels);
	}
}
