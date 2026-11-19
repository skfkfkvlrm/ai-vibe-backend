package com.oraclejava.demoboard.account;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

/** 회원 본인 기능: 비밀번호 변경, 정보 수정(이름은 이전 글에도 반영), 탈퇴(익명화), 내가 쓴 글. */
@SpringBootTest
class AccountApiTests {

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
	private Long userId;
	private Long otherId;
	private String userAuth;
	private String otherAuth;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		memberService.signup("user_01", PASS, "회원", "user@example.com");
		memberService.signup("other_01", PASS, "타인", "other@example.com");
		userId = idOf("user_01");
		otherId = idOf("other_01");
		userAuth = bearer("user_01");
		otherAuth = bearer("other_01");
	}

	private Long idOf(String loginId) {
		return memberRepository.findByLoginId(loginId).orElseThrow().getMemberId();
	}

	private String bearer(String loginId) {
		return "Bearer " + jwtProvider.issue(memberRepository.findByLoginId(loginId).orElseThrow());
	}

	private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder b, String json, String auth) {
		b.contentType(JSON).content(json);
		if (auth != null) b.header("Authorization", auth);
		return b;
	}

	private long writePost(String auth, String subject) throws Exception {
		String res = mvc.perform(body(post("/api/boards"), "{\"board_subject\":\"" + subject + "\",\"board_content\":\"내용\"}", auth))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	private String signupJson(String loginId, String email) {
		return "{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASS + "\",\"name\":\"가입자\",\"email\":\"" + email + "\"}";
	}

	/** 비밀번호 변경: 현재 비밀번호 확인, 규칙 검사, 기존 토큰 무효 + 새 토큰으로 현재 로그인 유지, 초기화 안내 플래그 해제. */
	@Test
	void changePasswordInvalidatesOldTokensAndReturnsNewOne() throws Exception {
		jdbc.update("update member set password_reset_pending = true where member_id = ?", userId);

		String url = "/api/members/me/password";
		mvc.perform(body(post(url), "{\"current_password\":\"wrong-pass-1\",\"new_password\":\"newpass1234\"}", userAuth))
				.andExpect(status().isForbidden());
		mvc.perform(body(post(url), "{\"current_password\":\"" + PASS + "\",\"new_password\":\"" + PASS + "\"}", userAuth))
				.andExpect(status().isBadRequest());
		mvc.perform(body(post(url), "{\"current_password\":\"" + PASS + "\",\"new_password\":\"short\"}", userAuth))
				.andExpect(status().isBadRequest());
		mvc.perform(body(post(url), "{\"current_password\":\"" + PASS + "\",\"new_password\":\"newpass1234\"}", null))
				.andExpect(status().isUnauthorized());

		String res = mvc.perform(body(post(url), "{\"current_password\":\"" + PASS + "\",\"new_password\":\"newpass1234\"}", userAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andReturn().getResponse().getContentAsString();
		String newAuth = "Bearer " + JsonPath.read(res, "$.access_token");

		mvc.perform(get("/api/auth/me").header("Authorization", userAuth)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", newAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password_reset_pending").value(false));
		mvc.perform(body(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + PASS + "\"}", null))
				.andExpect(status().isUnauthorized());
		mvc.perform(body(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"newpass1234\"}", null))
				.andExpect(status().isOk());
	}

	/** 이름을 바꾸면 내 모든 글(삭제된 글 포함)의 작성자명이 갱신되고 남의 글은 그대로. 이메일 중복·형식·예약 도메인 검증. */
	@Test
	void profileUpdatePropagatesNameToAllMyPosts() throws Exception {
		long mine1 = writePost(userAuth, "내 글1");
		long mine2 = writePost(userAuth, "내 글2");
		long others = writePost(otherAuth, "남의 글");
		mvc.perform(delete("/api/boards/" + mine2).header("Authorization", userAuth)).andExpect(status().isNoContent());

		mvc.perform(body(patch("/api/members/me"), "{\"name\":\"새이름\"}", userAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("새이름"))
				.andExpect(jsonPath("$.login_id").value("user_01"));

		List<String> names = jdbc.queryForList("select board_name from board where member_id = ?", String.class, userId);
		Assertions.assertEquals(2, names.size());
		Assertions.assertTrue(names.stream().allMatch("새이름"::equals));
		Assertions.assertEquals("타인", jdbc.queryForObject("select board_name from board where board_num = ?", String.class, others));
		Assertions.assertNotEquals(mine1, mine2);

		mvc.perform(body(patch("/api/members/me"), "{\"email\":\"NEW@Example.com\"}", userAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"));
		mvc.perform(body(patch("/api/members/me"), "{\"email\":\"other@example.com\"}", userAuth))
				.andExpect(status().isConflict());
		mvc.perform(body(patch("/api/members/me"), "{\"email\":\"not-an-email\"}", userAuth))
				.andExpect(status().isBadRequest());
		mvc.perform(body(patch("/api/members/me"), "{\"email\":\"x@withdrawn.invalid\"}", userAuth))
				.andExpect(status().isBadRequest());
		mvc.perform(body(patch("/api/members/me"), "{\"name\":\" \"}", userAuth)).andExpect(status().isBadRequest());
		mvc.perform(body(patch("/api/members/me"), "{}", userAuth)).andExpect(status().isBadRequest());
		mvc.perform(body(patch("/api/members/me"), "{\"name\":\"x\"}", null)).andExpect(status().isUnauthorized());
	}

	/** 탈퇴: 비밀번호 재확인, 개인정보 익명화, 글은 '탈퇴한 회원'으로 유지, 기존 로그인 무효, 같은 아이디·이메일로 재가입 가능. */
	@Test
	void withdrawAnonymizesMemberButKeepsPosts() throws Exception {
		long post = writePost(userAuth, "남는 글");

		mvc.perform(body(delete("/api/members/me"), "{\"password\":\"wrong-pass-1\"}", userAuth))
				.andExpect(status().isForbidden());
		mvc.perform(body(delete("/api/members/me"), "{\"password\":\"" + PASS + "\"}", userAuth))
				.andExpect(status().isNoContent());

		Assertions.assertEquals("withdrawn_" + userId,
				jdbc.queryForObject("select login_id from member where member_id = ?", String.class, userId));
		Assertions.assertEquals("withdrawn_" + userId + "@withdrawn.invalid",
				jdbc.queryForObject("select email from member where member_id = ?", String.class, userId));
		Assertions.assertEquals("탈퇴한 회원",
				jdbc.queryForObject("select name from member where member_id = ?", String.class, userId));
		Assertions.assertNotNull(
				jdbc.queryForObject("select withdrawn_at from member where member_id = ?", java.sql.Timestamp.class, userId));
		Assertions.assertEquals("탈퇴한 회원",
				jdbc.queryForObject("select board_name from board where board_num = ?", String.class, post));
		Assertions.assertEquals(userId,
				jdbc.queryForObject("select member_id from board where board_num = ?", Long.class, post));

		mvc.perform(get("/api/auth/me").header("Authorization", userAuth)).andExpect(status().isUnauthorized());
		mvc.perform(body(post("/api/auth/login"), "{\"login_id\":\"user_01\",\"password\":\"" + PASS + "\"}", null))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$.items[0].board_name").value("탈퇴한 회원"));

		// 아이디·이메일이 비워져 재가입할 수 있다.
		mvc.perform(body(post("/api/auth/signup"), signupJson("user_01", "user@example.com"), null))
				.andExpect(status().isCreated());
	}

	/** 탈퇴 아이디 형식·예약 도메인은 가입에 쓸 수 없고, 관리자는 탈퇴할 수 없다. */
	@Test
	void reservedNamesAndAdminCannotWithdraw() throws Exception {
		mvc.perform(body(post("/api/auth/signup"), signupJson("withdrawn_99", "free@example.com"), null))
				.andExpect(status().isBadRequest());
		mvc.perform(body(post("/api/auth/signup"), signupJson("free_user", "someone@withdrawn.invalid"), null))
				.andExpect(status().isBadRequest());

		jdbc.update("update member set role = 'ADMIN' where member_id = ?", otherId);
		mvc.perform(body(delete("/api/members/me"), "{\"password\":\"" + PASS + "\"}", otherAuth))
				.andExpect(status().isForbidden());
		Assertions.assertEquals("other_01",
				jdbc.queryForObject("select login_id from member where member_id = ?", String.class, otherId));
	}

	/** 내가 쓴 글 목록: 내 글·답글만, 삭제된 글 제외, 비로그인 401. */
	@Test
	void myPostsListsOnlyMyActivePosts() throws Exception {
		long a = writePost(userAuth, "내 원글");
		long deleted = writePost(userAuth, "삭제할 글");
		writePost(otherAuth, "남의 글");
		mvc.perform(body(post("/api/boards/" + a + "/replies"), "{\"board_subject\":\"내 답글\"}", userAuth))
				.andExpect(status().isCreated());
		mvc.perform(delete("/api/boards/" + deleted).header("Authorization", userAuth)).andExpect(status().isNoContent());

		mvc.perform(get("/api/members/me/posts").header("Authorization", userAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.board_subject=='남의 글')]").isEmpty())
				.andExpect(jsonPath("$[?(@.board_subject=='삭제할 글')]").isEmpty())
				.andExpect(jsonPath("$[?(@.board_subject=='내 답글')].re_lev").value(1));
		mvc.perform(get("/api/members/me/posts")).andExpect(status().isUnauthorized());
	}
}
