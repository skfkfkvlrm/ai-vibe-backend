package com.oraclejava.pc_mall.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 역할 변경 요청. role은 USER 또는 ADMIN. */
public record RoleRequest(@JsonProperty("role") String role) {
}
