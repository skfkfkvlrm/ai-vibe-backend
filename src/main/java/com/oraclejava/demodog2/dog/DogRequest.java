package com.oraclejava.demodog2.dog;

/** 개 생성/수정 요청 본문. id와 readcount는 서버가 관리하므로 받지 않는다. */
public record DogRequest(String kind, Integer price, String image, String country,
		Double height, Double weight, String content) {
}
