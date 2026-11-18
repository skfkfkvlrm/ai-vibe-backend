package com.oraclejava.demo_boot.note;

/** 노트 생성/수정 요청 본문. */
public record NoteRequest(String title, String content) {
}
