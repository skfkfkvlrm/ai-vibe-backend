package com.oraclejava.demodog.dog;

import java.time.LocalDateTime;

/** 삭제된 개 목록 API 응답 본문. 복구 화면에 필요한 항목만 담는다. */
public record DeletedDog(Long id, String kind, Integer price, String image, LocalDateTime deletedAt) {
}
