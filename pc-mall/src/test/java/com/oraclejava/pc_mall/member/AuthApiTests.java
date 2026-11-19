package com.oraclejava.pc_mall.member;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 로그인 1단계: 가입·로그인·잠금·밴·토큰 무효화와 상품 API 공개 회귀. 각 테스트는 롤백된다. */
@SpringBootTest
@Transactional
class AuthApiTests {

	private static final String LOGIN_ID = "qa_user01";
	private static final String PASSWORD = "pw123456";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private JwtProvider jwtProvider;

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

	private org.springframework.test.web.servlet.ResultActions login(String loginId, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
	}

	/** T1: 가입 후 올바른 로그인은 200+토큰, 틀린 비밀번호는 401. */
	@Test
	void loginSucceedsWithCorrectPasswordAndFailsWithWrongOne() throws Exception {
		signup(LOGIN_ID, "qa01@example.com");
		login(LOGIN_ID, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty())
				.andExpect(jsonPath("$.token_type").value("Bearer"));
		login(LOGIN_ID, "wrong-password").andExpect(status().isUnauthorized());
		login("qa_nobody", PASSWORD).andExpect(status().isUnauthorized());
	}

	/** T2: 같은 아이디로 5회 연속 실패하면 올바른 비밀번호도 429 LOGIN_LOCKED. */
	@Test
	void fiveFailuresLockLoginEvenWithCorrectPassword() throws Exception {
		signup(LOGIN_ID, "qa01@example.com");
		for (int i = 0; i < 5; i++) {
			login(LOGIN_ID, "wrong-password").andExpect(status().isUnauthorized());
		}
		login(LOGIN_ID, PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
	}

	/** T3: 밴 회원은 올바른 로그인이어도 토큰 없이 403 + 밴 안내. */
	@Test
	void bannedMemberGetsForbiddenWithBanInfo() throws Exception {
		signup(LOGIN_ID, "qa01@example.com");
		MemberEntity member = memberRepository.findByLoginId(LOGIN_ID).orElseThrow();
		member.ban("테스트 밴", LocalDateTime.now(), null);
		memberRepository.saveAndFlush(member);
		login(LOGIN_ID, PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BANNED"))
				.andExpect(jsonPath("$.permanent").value(true)).andExpect(jsonPath("$.access_token").doesNotExist());
	}

	/** T4: 유효 토큰은 me 200, 토큰 없음·만료·위조·ver 불일치(비밀번호 변경 후)는 401. */
	@Test
	void meRequiresValidToken() throws Exception {
		signup(LOGIN_ID, "qa01@example.com");
		MemberEntity member = memberRepository.findByLoginId(LOGIN_ID).orElseThrow();
		String valid = jwtProvider.issue(member);
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + valid)).andExpect(status().isOk())
				.andExpect(jsonPath("$.login_id").value(LOGIN_ID)).andExpect(jsonPath("$.password").doesNotExist());

		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		String expired = jwtProvider.issue(member.getMemberId(), member.getRole(), Duration.ofSeconds(-10));
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
		// 페이로드 한 글자를 바꾼 위조 토큰(서명이 맞지 않음)
		String[] parts = valid.split("\\.");
		char c = parts[1].charAt(10);
		String forged = parts[0] + "." + parts[1].substring(0, 10) + (c == 'A' ? 'B' : 'A') + parts[1].substring(11)
				+ "." + parts[2];
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
				.andExpect(status().isUnauthorized());

		member.changePassword("{noop}changed");
		memberRepository.saveAndFlush(member);
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + valid))
				.andExpect(status().isUnauthorized());
	}

	/** T5: 토큰 없이 상품 조회·등록은 기존대로 공개. 만료 토큰이 붙은 조회도 공개로 동작한다. */
	@Test
	void productApiStaysPublic() throws Exception {
		mvc.perform(get("/health")).andExpect(status().isOk());
		mvc.perform(get("/api/products")).andExpect(status().isOk());
		mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"QA\",\"price\":1,\"kind\":\"laptop\"}")).andExpect(status().isCreated());
		mvc.perform(get("/api/products").header("Authorization", "Bearer invalid.token.value"))
				.andExpect(status().isOk());
	}

	/** T6: 중복 아이디·이메일 가입은 409, 잘못된 입력은 400. */
	@Test
	void duplicateSignupIsConflict() throws Exception {
		signup(LOGIN_ID, "qa01@example.com");
		String body = "{\"login_id\":\"%s\",\"password\":\"" + PASSWORD + "\",\"name\":\"회원\",\"email\":\"%s\"}";
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(body.formatted(LOGIN_ID, "other@example.com"))).andExpect(status().isConflict());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(body.formatted("qa_user02", "qa01@example.com"))).andExpect(status().isConflict());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(body.formatted("A", "x@example.com"))).andExpect(status().isBadRequest());
	}
}
