package com.oraclejava.pc_mall.order;

import java.util.List;

/** 주문 취소 요청(사유 및 취소할 주문 줄 목록). */
public record CancelRequest(String reason, List<Item> items) {

	public record Item(Long lineId, int quantity) {
	}
}
