package com.oraclejava.demoboard.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 관리자의 회원 밴(영구·기간)과 비밀번호 초기화. 밴은 인증 요청마다 DB로 확인해 즉시 적용된다. */
@SpringBootTest
class MemberManagementTests {

	private static final String PASS = "pw123456";
	private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private BoardRepository boardRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private MemberService memberService;
	@Autowired
	private JwtProvider jwtProvider;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private String adminAuth;
	private Long userId;
	private Long otherId;
	private Long adminId;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		memberService.signup("admin_01", PASS, "관리자", "admin@example.com");
		memberService.signup("user_01", PASS, "대상회원", "user@example.com");
		memberService.signup("other_01", PASS, "다른회원", "other@example.com");
		jdbc.update("update member set role = 'ADMIN' where login_id = 'admin_01'");
		adminAuth = bearer("admin_01");
		adminId = idOf("admin_01");
		userId = idOf("user_01");
		otherId = idOf("other_01");
	}

	private Long idOf(String loginId) {
		return memberRepository.findByLoginId(loginId).orElseThrow().getMemberId();
	}

	private String bearer(String loginId) {
		return "Bearer " + jwtProvider.issue(memberRepository.findByLoginId(loginId).orElseThrow());
	}

	private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder b, String body, String auth) {
		b.contentType(JSON).content(body);
		if (auth != null) b.header("Authorization", auth);
		return b;
	}

	private String ban(Long id, String body) {
		return "/api/admin/members/" + id + "/ban";
	}

	private void memberWrites(String auth, int expectedStatus) throws Exception {
		mvc.perform(withBody(post("/api/boards"), "{\"board_subject\":\"글\",\"board_content\":\"내용\"}", auth))
				.andExpect(status().is(expectedStatus));
	}

	/** 밴 직후 같은 토큰으로 쓰기는 403(BANNED), 공개 조회는 가능, 로그인·/me는 막히고, 해제하면 바로 정상. */
	@Test
	void banTakesEffectImmediatelyAndCanBeReleased() throws Exception {
		String userAuth = bearer("user_01");
		memberWrites(userAuth, 201);

		mvc.perform(withBody(post(ban(userId, null)), "{\"reason\":\"욕설\",\"type\":\"7D\"}", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BANNED"))
				.andExpect(jsonPath("$.ban_reason").value("욕설"))
				.andExpect(jsonPath("$.banned_permanent").value(false))
				.andExpect(jsonPath("$.banned_until").exists());

		mvc.perform(withBody(post("/api/boards"), "{\"board_subject\":\"글\"}", userAuth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BANNED"))
				.andExpect(jsonPath("$.reason").value("욕설"));
		mvc.perform(get("/api/boards").header("Authorization", userAuth)).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").header("Authorization", userAuth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BANNED"));
		mvc.perform(withBody(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + PASS + "\"}", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BANNED"))
				.andExpect(jsonPath("$.reason").value("욕설"));
		// 비밀번호가 틀리면 밴 여부를 알려주지 않고 401
		mvc.perform(withBody(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"wrong-pass-1\"}", null))
				.andExpect(status().isUnauthorized());

		mvc.perform(get("/api/admin/members").header("Authorization", adminAuth))
				.andExpect(jsonPath("$[?(@.login_id=='user_01')].status").value("BANNED"));

		mvc.perform(delete(ban(userId, null)).header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.ban_reason").doesNotExist());
		mvc.perform(withBody(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + PASS + "\"}", null))
				.andExpect(status().isOk());
		memberWrites(userAuth, 201);
	}

	/** 영구·기간(프리셋·직접 입력) 밴, 입력 검증, 기간 만료 시 자동 해제. */
	@Test
	void banTypesValidationAndAutomaticExpiry() throws Exception {
		String userAuth = bearer("user_01");

		mvc.perform(withBody(post(ban(userId, null)), "{\"reason\":\"반복 위반\",\"type\":\"PERMANENT\"}", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BANNED"))
				.andExpect(jsonPath("$.banned_permanent").value(true))
				.andExpect(jsonPath("$.banned_until").doesNotExist());
		memberWrites(userAuth, 403);

		for (String type : new String[] { "1D", "30D" }) {
			mvc.perform(withBody(post(ban(userId, null)), "{\"reason\":\"테스트\",\"type\":\"" + type + "\"}", adminAuth))
					.andExpect(status().isOk()).andExpect(jsonPath("$.banned_permanent").value(false));
		}
		String future = LocalDateTime.now().plusDays(3).withNano(0).toString();
		mvc.perform(withBody(post(ban(userId, null)), "{\"reason\":\"직접\",\"type\":\"UNTIL\",\"until\":\"" + future + "\"}", adminAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.banned_until").exists());

		String past = LocalDateTime.now().minusDays(1).withNano(0).toString();
		String[] bad = {
				"{\"reason\":\"x\",\"type\":\"UNTIL\",\"until\":\"" + past + "\"}",
				"{\"reason\":\"x\",\"type\":\"UNTIL\"}",
				"{\"reason\":\" \",\"type\":\"7D\"}",
				"{\"type\":\"7D\"}",
				"{\"reason\":\"x\",\"type\":\"FOREVER\"}" };
		for (String body : bad) {
			mvc.perform(withBody(post(ban(userId, null)), body, adminAuth)).andExpect(status().isBadRequest());
		}

		// 기간이 지나면 별도 작업 없이 해제된 것으로 판정된다.
		jdbc.update("update member set banned_until = ? where member_id = ?", LocalDateTime.now().minusMinutes(1), userId);
		memberWrites(userAuth, 201);
		mvc.perform(get("/api/admin/members").header("Authorization", adminAuth))
				.andExpect(jsonPath("$[?(@.login_id=='user_01')].status").value("ACTIVE"));
	}

	/** 관리자·본인·없는 회원은 밴할 수 없고, 관리자가 아니면 호출 자체가 403. */
	@Test
	void banTargetAndCallerRestrictions() throws Exception {
		String body = "{\"reason\":\"x\",\"type\":\"7D\"}";
		mvc.perform(withBody(post(ban(adminId, null)), body, adminAuth)).andExpect(status().isForbidden());

		jdbc.update("update member set role = 'ADMIN' where member_id = ?", otherId);
		mvc.perform(withBody(post(ban(otherId, null)), body, adminAuth)).andExpect(status().isForbidden());
		jdbc.update("update member set role = 'USER' where member_id = ?", otherId);

		mvc.perform(withBody(post(ban(999999L, null)), body, adminAuth)).andExpect(status().isNotFound());
		mvc.perform(withBody(post(ban(userId, null)), body, bearer("other_01"))).andExpect(status().isForbidden());
		mvc.perform(withBody(post(ban(userId, null)), body, null)).andExpect(status().isUnauthorized());
		Assertions.assertEquals(0, memberRepository.findAll().stream().filter(m -> m.isBanned(LocalDateTime.now())).count());
	}

	/** 비밀번호 초기화: 임시 비밀번호는 응답에 한 번만, 기존 로그인은 모두 무효, 임시 비밀번호로 로그인하면 변경 안내 플래그. */
	@Test
	void adminPasswordResetIssuesTemporaryPasswordAndInvalidatesSessions() throws Exception {
		String oldAuth = bearer("user_01");
		mvc.perform(get("/api/auth/me").header("Authorization", oldAuth)).andExpect(status().isOk());

		String res = mvc.perform(post("/api/admin/members/" + userId + "/reset-password").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(jsonPath("$.temporary_password").exists())
				.andReturn().getResponse().getContentAsString();
		String temp = JsonPath.read(res, "$.temporary_password");
		Assertions.assertEquals(12, temp.length());
		Assertions.assertNotEquals(PASS, temp);

		mvc.perform(get("/api/auth/me").header("Authorization", oldAuth)).andExpect(status().isUnauthorized());
		mvc.perform(withBody(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + PASS + "\"}", null))
				.andExpect(status().isUnauthorized());

		String login = mvc.perform(withBody(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + temp + "\"}", null))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(login, "$.access_token");
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password_reset_pending").value(true));
		mvc.perform(get("/api/admin/members").header("Authorization", adminAuth))
				.andExpect(jsonPath("$[?(@.login_id=='user_01')].password_reset_pending").value(true));

		// 관리자·본인은 초기화할 수 없고, 일반 회원은 403
		mvc.perform(post("/api/admin/members/" + adminId + "/reset-password").header("Authorization", adminAuth))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/members/" + userId + "/reset-password").header("Authorization", bearer("other_01")))
				.andExpect(status().isForbidden());
	}
}
