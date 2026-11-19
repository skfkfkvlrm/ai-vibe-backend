package com.oraclejava.pc_mall.member;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 회원 입력 규칙. 위반 시 400. */
public final class MemberRules {

	private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{4,20}$");
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int MIN_PASSWORD = 8;
	/** BCrypt는 72바이트까지만 비교하므로 그 이상은 받지 않는다. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private MemberRules() {
	}

	/** 4~20자 영문 소문자·숫자·_ */
	public static void loginId(String value) {
		if (value == null || !LOGIN_ID.matcher(value).matches()) {
			throw bad("login_id must be 4-20 chars of a-z, 0-9, _");
		}
	}

	public static void password(String value) {
		if (value == null || value.length() < MIN_PASSWORD
				|| value.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw bad("password must be at least 8 characters");
		}
	}

	/** 공백을 제거한 이름을 돌려준다. */
	public static String name(String value) {
		if (value == null || value.isBlank() || value.trim().length() > 100) {
			throw bad("name is required");
		}
		return value.trim();
	}

	/** 공백을 제거하고 소문자로 맞춘 이메일을 돌려준다. */
	public static String email(String value) {
		if (value == null || value.isBlank()) {
			throw bad("email is invalid");
		}
		String email = value.trim().toLowerCase();
		if (email.length() > 200 || !EMAIL.matcher(email).matches()) {
			throw bad("email is invalid");
		}
		return email;
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
