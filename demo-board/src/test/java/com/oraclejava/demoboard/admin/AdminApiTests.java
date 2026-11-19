package com.oraclejava.demoboard.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 관리자 API(삭제된 글 목록·복구, 회원 목록)와 관리자의 글 수정·삭제 권한. 권한은 요청마다 DB의 role로 판정한다. */
@SpringBootTest
class AdminApiTests {

	private static final String PASS = "pw1234";
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
	private String userAuth;
	private String otherAuth;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		memberService.signup("admin_01", "pw123456", "관리자", "admin@example.com");
		memberService.signup("user_01", "pw123456", "일반회원", "user@example.com");
		memberService.signup("other_01", "pw123456", "타인", "other@example.com");
		setRole("admin_01", "ADMIN");
		adminAuth = bearer("admin_01");
		userAuth = bearer("user_01");
		otherAuth = bearer("other_01");
	}

	private void setRole(String loginId, String role) {
		jdbc.update("update member set role = ? where login_id = ?", role, loginId);
	}

	private String bearer(String loginId) {
		return "Bearer " + jwtProvider.issue(memberRepository.findByLoginId(loginId).orElseThrow());
	}

	private long createGuest(String subject) throws Exception {
		String body = "{\"board_name\":\"손님\",\"board_pass\":\"" + PASS + "\",\"board_subject\":\"" + subject
				+ "\",\"board_content\":\"내용\"}";
		String res = mvc.perform(post("/api/boards").contentType(JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	private long createByMember(String url, String subject, String auth) throws Exception {
		String body = "{\"board_subject\":\"" + subject + "\",\"board_content\":\"내용\"}";
		String res = mvc.perform(post(url).contentType(JSON).content(body).header("Authorization", auth))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	private void guestDelete(long id) throws Exception {
		mvc.perform(delete("/api/boards/" + id).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());
	}

	/** 비로그인 401, 일반 회원 403, 관리자 200. 판정은 토큰이 아니라 DB의 현재 role이다. */
	@Test
	void adminEndpointsRequireAdminRoleFromDatabase() throws Exception {
		String[] urls = { "/api/admin/members", "/api/admin/boards/deleted" };
		for (String url : urls) {
			mvc.perform(get(url)).andExpect(status().isUnauthorized());
			mvc.perform(get(url).header("Authorization", userAuth)).andExpect(status().isForbidden());
			mvc.perform(get(url).header("Authorization", adminAuth)).andExpect(status().isOk());
		}
		mvc.perform(post("/api/admin/boards/1/restore")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/admin/boards/1/restore").header("Authorization", userAuth))
				.andExpect(status().isForbidden());

		// 회원 목록: 3명, 비밀번호·해시 없음
		mvc.perform(get("/api/admin/members").header("Authorization", adminAuth))
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].password").doesNotExist())
				.andExpect(jsonPath("$[0].login_id").exists())
				.andExpect(jsonPath("$[0].email").exists());

		// 승격 직후에는 승격 전에 발급된 토큰으로도 접근되고, 강등 직후에는 바로 막힌다.
		mvc.perform(get("/api/admin/members").header("Authorization", userAuth)).andExpect(status().isForbidden());
		setRole("user_01", "ADMIN");
		mvc.perform(get("/api/admin/members").header("Authorization", userAuth)).andExpect(status().isOk());
		setRole("user_01", "USER");
		mvc.perform(get("/api/admin/members").header("Authorization", userAuth)).andExpect(status().isForbidden());
	}

	/** 삭제된 글·답글 목록(root_deleted 표시)과 선택한 1건만 복구. */
	@Test
	void deletedListAndRestoreSingleItem() throws Exception {
		long root = createGuest("원글");
		long reply = createByMember("/api/boards/" + root + "/replies", "답글", userAuth);

		mvc.perform(get("/api/admin/boards/deleted").header("Authorization", adminAuth))
				.andExpect(jsonPath("$.length()").value(0));

		guestDelete(root);
		mvc.perform(delete("/api/boards/" + reply).header("Authorization", userAuth)).andExpect(status().isNoContent());

		// 삭제 시각 역순: 답글(나중에 삭제) -> 원글. 답글은 원글도 삭제 상태.
		mvc.perform(get("/api/admin/boards/deleted").header("Authorization", adminAuth))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].board_num").value(reply))
				.andExpect(jsonPath("$[0].re_lev").value(1))
				.andExpect(jsonPath("$[0].root_deleted").value(true))
				.andExpect(jsonPath("$[0].deleted_at").exists())
				.andExpect(jsonPath("$[1].board_num").value(root))
				.andExpect(jsonPath("$[1].root_deleted").value(false));

		// 원글만 복구하면 답글은 그대로 삭제 상태
		mvc.perform(post("/api/admin/boards/" + root + "/restore").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_num").value(root));
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].board_num").value(root));
		mvc.perform(get("/api/admin/boards/deleted").header("Authorization", adminAuth))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].board_num").value(reply))
				.andExpect(jsonPath("$[0].root_deleted").value(false));

		// 삭제되지 않은 글, 없는 글은 404
		mvc.perform(post("/api/admin/boards/" + root + "/restore").header("Authorization", adminAuth))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/admin/boards/999999/restore").header("Authorization", adminAuth))
				.andExpect(status().isNotFound());
	}

	/** 관리자는 남의 회원 글·비회원 글을 비밀번호 없이 수정·삭제한다. 작성자와 소유자는 유지된다. 일반 회원은 여전히 403. */
	@Test
	void adminCanEditAndDeleteAnyPostButAuthorStays() throws Exception {
		long memberPost = createByMember("/api/boards", "회원 글", userAuth);
		long guestPost = createGuest("비회원 글");
		String body = "{\"board_subject\":\"관리자 수정\",\"board_name\":\"위조\"}";

		mvc.perform(patch("/api/boards/" + memberPost).contentType(JSON).content(body).header("Authorization", otherAuth))
				.andExpect(status().isForbidden());

		mvc.perform(patch("/api/boards/" + memberPost).contentType(JSON).content(body).header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_subject").value("관리자 수정"))
				.andExpect(jsonPath("$.board_name").value("일반회원"))
				.andExpect(jsonPath("$.member_id").value(memberRepository.findByLoginId("user_01").orElseThrow().getMemberId()));
		mvc.perform(patch("/api/boards/" + guestPost).contentType(JSON).content(body).header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_subject").value("관리자 수정"))
				.andExpect(jsonPath("$.board_name").value("손님"))
				.andExpect(jsonPath("$.member_id").doesNotExist());

		mvc.perform(delete("/api/boards/" + memberPost).header("Authorization", adminAuth)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/boards/" + guestPost).header("Authorization", adminAuth)).andExpect(status().isNoContent());
		Assertions.assertEquals(2, boardRepository.count());
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$.length()").value(0));
	}
}
