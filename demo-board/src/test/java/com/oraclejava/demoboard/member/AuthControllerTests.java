package com.oraclejava.demoboard.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;

/** 회원가입·로그인·내 정보 API 통합 테스트 (H2, MySQL 호환 모드). */
@SpringBootTest
class AuthControllerTests {

	private static final String PASS = "pw123456";

	@Autowired
	private AuthController authController;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(authController).build();
	}

	private static String signupJson(String loginId, String password, String name, String email) {
		return "{\"login_id\":\"" + loginId + "\",\"password\":\"" + password
				+ "\",\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
	}

	private static String loginJson(String loginId, String password) {
		return "{\"login_id\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
	}

	private void signup(String loginId, String email) throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(signupJson(loginId, PASS, "홍길동", email)))
				.andExpect(status().isCreated());
	}

	private String login(String loginId) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(loginId, PASS)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.access_token");
	}

	/** 가입: 201, 응답에 비밀번호 없음, DB에는 BCrypt 해시, 아이디·이메일 중복은 409. */
	@Test
	void signupStoresHashAndRejectsDuplicates() throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(signupJson("user_01", PASS, "홍길동", "hong@example.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.login_id").value("user_01"))
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.email").value("hong@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.created_at").exists())
				.andExpect(jsonPath("$.password").doesNotExist());

		String hash = memberRepository.findByLoginId("user_01").orElseThrow().getPassword();
		Assertions.assertNotEquals(PASS, hash);
		Assertions.assertTrue(hash.startsWith("$2"));

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(signupJson("user_01", PASS, "다른사람", "other@example.com")))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(signupJson("user_02", PASS, "다른사람", "hong@example.com")))
				.andExpect(status().isConflict());
	}

	/** 로그인 성공 시 토큰 발급, 그 토큰으로 /me 조회. 비밀번호 오류와 없는 아이디는 같은 401. */
	@Test
	void loginIssuesTokenAndMeWorks() throws Exception {
		signup("user_01", "hong@example.com");

		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("user_01", PASS)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.expires_in").value(7200))
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.access_token");

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.login_id").value("user_01"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist());

		String wrongPass = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("user_01", "wrong-pass")))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		String unknownId = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("nobody", PASS)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		Assertions.assertEquals(wrongPass, unknownId);
	}

	/** /me: 토큰 없음·형식 오류·위조·만료·삭제된 회원은 모두 401. */
	@Test
	void meRejectsBadTokens() throws Exception {
		signup("user_01", "hong@example.com");
		String token = login("user_01");
		Long memberId = memberRepository.findByLoginId("user_01").orElseThrow().getMemberId();

		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"))
				.andExpect(status().isUnauthorized());

		String expired = jwtProvider.issue(memberId, "USER", Duration.ofSeconds(-60));
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());

		memberRepository.deleteAll();
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	/** 가입 입력 검증: 아이디 형식, 비밀번호 8자 미만, 이름 공백, 이메일 형식 → 400. */
	@Test
	void signupValidation() throws Exception {
		String[] bad = {
				signupJson("ab", PASS, "홍길동", "a@example.com"),
				signupJson("UPPER", PASS, "홍길동", "a@example.com"),
				signupJson("user_01", "short", "홍길동", "a@example.com"),
				signupJson("user_01", PASS, " ", "a@example.com"),
				signupJson("user_01", PASS, "홍길동", "not-an-email"),
				"{\"login_id\":\"user_01\"}" };
		for (String json : bad) {
			mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		Assertions.assertEquals(0, memberRepository.count());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	/** 로그인 응답 본문에 비밀번호·해시가 포함되지 않는다. */
	@Test
	void tokenResponseHasNoSecrets() throws Exception {
		signup("user_01", "hong@example.com");
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("user_01", PASS)))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2"))))
				.andExpect(jsonPath("$.password").doesNotExist());
	}
}
