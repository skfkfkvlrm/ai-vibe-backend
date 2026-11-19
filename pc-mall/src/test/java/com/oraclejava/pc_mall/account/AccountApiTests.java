package com.oraclejava.pc_mall.account;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 마이페이지 API: 내 정보 수정, 비밀번호 변경(이전 토큰 무효화). 각 테스트는 롤백된다. */
@SpringBootTest
@Transactional
class AccountApiTests {

	private static final String PASSWORD = "pw123456";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private void signup(String loginId, String email) throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
				"{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"회원\",\"email\":\""
						+ email + "\"}")).andExpect(status().isCreated());
	}

	/** 가입하고 로그인해 Bearer 토큰 문자열을 돌려준다. */
	private String signupAndLogin(String loginId, String email) throws Exception {
		signup(loginId, email);
		return loginToken(loginId, PASSWORD);
	}

	private String loginToken(String loginId, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String patchBody(String name, String email) {
		return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
	}

	private static String passwordBody(String current, String next) {
		return "{\"current_password\":\"" + current + "\",\"new_password\":\"" + next + "\"}";
	}

	/** T1: 이름·이메일 수정은 200이고 me 조회에 반영된다. */
	@Test
	void updateProfileIsReflectedInMe() throws Exception {
		String token = signupAndLogin("qa_acct01", "qa_acct01@example.com");
		mvc.perform(patch("/api/members/me").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(patchBody("새이름", "New_Mail@Example.com")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("새이름"))
				.andExpect(jsonPath("$.email").value("new_mail@example.com"));
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("새이름")).andExpect(jsonPath("$.email").value("new_mail@example.com"));
	}

	/** T2: 다른 회원이 쓰는 이메일로 바꾸면 409. 본인 이메일 그대로는 허용. */
	@Test
	void duplicateEmailIsConflict() throws Exception {
		signup("qa_acct02", "qa_acct02@example.com");
		String token = signupAndLogin("qa_acct03", "qa_acct03@example.com");
		mvc.perform(patch("/api/members/me").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(patchBody("회원", "qa_acct02@example.com")))
				.andExpect(status().isConflict());
		mvc.perform(patch("/api/members/me").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(patchBody("회원", "qa_acct03@example.com")))
				.andExpect(status().isOk());
	}

	/** T3: 비밀번호 변경은 새 토큰을 주고, 이전 토큰은 무효가 되며, 새 비밀번호로 로그인된다. */
	@Test
	void changePasswordIssuesNewTokenAndInvalidatesOld() throws Exception {
		String oldToken = signupAndLogin("qa_acct04", "qa_acct04@example.com");
		String body = mvc.perform(post("/api/members/me/password").header("Authorization", bearer(oldToken))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "newpw12345")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty()).andReturn()
				.getResponse().getContentAsString();
		String newToken = body.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken))).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(newToken))).andExpect(status().isOk());
		loginToken("qa_acct04", "newpw12345");
	}

	/** T4: 현재 비밀번호가 틀리면 403, 새 비밀번호가 현재와 같거나 8자 미만이면 400. */
	@Test
	void invalidPasswordChangeIsRejected() throws Exception {
		String token = signupAndLogin("qa_acct05", "qa_acct05@example.com");
		mvc.perform(post("/api/members/me/password").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody("wrong-password", "newpw12345")))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/members/me/password").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, PASSWORD)))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/members/me/password").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "short")))
				.andExpect(status().isBadRequest());
	}

	/** T5: 토큰 없이 호출하면 401. */
	@Test
	void requiresLogin() throws Exception {
		mvc.perform(patch("/api/members/me").contentType(MediaType.APPLICATION_JSON)
				.content(patchBody("회원", "x@example.com"))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/members/me/password").contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody(PASSWORD, "newpw12345"))).andExpect(status().isUnauthorized());
	}
}
