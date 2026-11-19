package com.oraclejava.demoboard.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

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

/** 관리자 쓰기 작업(복구·관리자 수정/삭제·밴·해제·비밀번호 초기화)의 감사 로그와 조회 권한. */
@SpringBootTest
class AdminAuditTests {

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
	private String userAuth;
	private Long adminId;
	private Long userId;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from admin_audit_log");
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		memberService.signup("admin_01", PASS, "관리자", "admin@example.com");
		memberService.signup("user_01", PASS, "회원", "user@example.com");
		jdbc.update("update member set role = 'ADMIN' where login_id = 'admin_01'");
		adminAuth = bearer("admin_01");
		userAuth = bearer("user_01");
		adminId = memberRepository.findByLoginId("admin_01").orElseThrow().getMemberId();
		userId = memberRepository.findByLoginId("user_01").orElseThrow().getMemberId();
	}

	private String bearer(String loginId) {
		return "Bearer " + jwtProvider.issue(memberRepository.findByLoginId(loginId).orElseThrow());
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body, String auth) {
		return b.contentType(JSON).content(body).header("Authorization", auth);
	}

	private long newPost() throws Exception {
		String res = mvc.perform(json(post("/api/boards"), "{\"board_subject\":\"글\",\"board_content\":\"내용\"}", userAuth))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	private List<Map<String, Object>> audit(String action) {
		return jdbc.queryForList("select * from admin_audit_log where action = ? order by audit_id", action);
	}

	/** T2: 관리자 작업마다 관리자 ID·동작·대상·시각이 1건씩 남고, 임시 비밀번호는 기록되지 않는다. */
	@Test
	void adminWritesAreAuditedWithoutSecrets() throws Exception {
		long post = newPost();
		// 작성자 본인의 삭제는 관리자 작업이 아니므로 기록하지 않는다.
		mvc.perform(delete("/api/boards/" + post).header("Authorization", userAuth)).andExpect(status().isNoContent());
		Assertions.assertEquals(0, audit("BOARD_DELETE").size());

		mvc.perform(post("/api/admin/boards/" + post + "/restore").header("Authorization", adminAuth)).andExpect(status().isOk());
		mvc.perform(json(put("/api/boards/" + post), "{\"board_subject\":\"수정\"}", adminAuth)).andExpect(status().isOk());
		mvc.perform(delete("/api/boards/" + post).header("Authorization", adminAuth)).andExpect(status().isNoContent());
		mvc.perform(json(post("/api/admin/members/" + userId + "/ban"), "{\"reason\":\"욕설\",\"type\":\"7D\"}", adminAuth))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/admin/members/" + userId + "/ban").header("Authorization", adminAuth)).andExpect(status().isOk());
		String res = mvc.perform(post("/api/admin/members/" + userId + "/reset-password").header("Authorization", adminAuth))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String temp = JsonPath.read(res, "$.temporary_password");

		for (String action : new String[] { "BOARD_RESTORE", "BOARD_EDIT", "BOARD_DELETE", "MEMBER_BAN", "MEMBER_UNBAN",
				"MEMBER_PASSWORD_RESET" }) {
			List<Map<String, Object>> rows = audit(action);
			Assertions.assertEquals(1, rows.size(), action);
			Assertions.assertEquals(adminId, ((Number) rows.get(0).get("admin_id")).longValue(), action);
			Assertions.assertNotNull(rows.get(0).get("created_at"), action);
		}
		Assertions.assertEquals(post, ((Number) audit("BOARD_RESTORE").get(0).get("target_id")).longValue());
		Assertions.assertEquals(userId, ((Number) audit("MEMBER_BAN").get(0).get("target_id")).longValue());
		Assertions.assertEquals(0, jdbc.queryForObject(
				"select count(*) from admin_audit_log where detail like ?", Integer.class, "%" + temp + "%"));
	}

	/** T4: 조회는 관리자만(비로그인 401, 일반 회원 403), 최신순이고 관리자 아이디가 함께 나온다. */
	@Test
	void auditLogListIsAdminOnlyAndNewestFirst() throws Exception {
		mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/audit-logs").header("Authorization", userAuth)).andExpect(status().isForbidden());

		mvc.perform(json(post("/api/admin/members/" + userId + "/ban"), "{\"reason\":\"욕설\",\"type\":\"7D\"}", adminAuth))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/admin/members/" + userId + "/ban").header("Authorization", adminAuth)).andExpect(status().isOk());

		mvc.perform(get("/api/admin/audit-logs").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].action").value("MEMBER_UNBAN"))
				.andExpect(jsonPath("$[1].action").value("MEMBER_BAN"))
				.andExpect(jsonPath("$[0].admin_login_id").value("admin_01"))
				.andExpect(jsonPath("$[0].target_type").value("MEMBER"))
				.andExpect(jsonPath("$[0].target_id").value(userId.intValue()));
	}
}
