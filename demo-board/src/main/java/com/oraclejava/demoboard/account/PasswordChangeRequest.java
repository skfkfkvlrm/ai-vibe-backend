package com.oraclejava.demoboard.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordChangeRequest(
		@JsonProperty("current_password") String currentPassword,
		@JsonProperty("new_password") String newPassword) {
}
