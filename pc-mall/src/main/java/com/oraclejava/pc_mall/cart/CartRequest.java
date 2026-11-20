package com.oraclejava.pc_mall.cart;

/** 담기(productId + quantity) / 수량 변경(quantity) 공용 요청. */
public record CartRequest(Long productId, Integer quantity) {
}
