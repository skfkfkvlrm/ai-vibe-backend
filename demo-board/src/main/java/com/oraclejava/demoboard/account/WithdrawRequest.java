package com.oraclejava.demoboard.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 탈퇴 요청. 본인 확인을 위해 비밀번호를 다시 받는다. */
public record WithdrawRequest(@JsonProperty("password") String password) {
}
