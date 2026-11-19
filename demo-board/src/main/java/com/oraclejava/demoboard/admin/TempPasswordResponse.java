package com.oraclejava.demoboard.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 비밀번호 초기화 결과. 임시 비밀번호는 이 응답으로 한 번만 전달되며 서버에 평문으로 남지 않는다. */
public record TempPasswordResponse(@JsonProperty("temporary_password") String temporaryPassword) {
}
