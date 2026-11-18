package com.oraclejava.demodog2.dog;

/** 개 리소스 API 응답 본문. */
public record Dog(Long id, String kind, Integer price, String image, String country,
		Double height, Double weight, String content, Long readcount) {
}
