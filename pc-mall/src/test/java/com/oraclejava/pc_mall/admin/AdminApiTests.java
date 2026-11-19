package com.oraclejava.pc_mall.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 회원 관리 API. 각 테스트는 롤백되므로 실제 회원 데이터는 바뀌지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiTests {

	private static final String PASSWORD = "pw123456";
	private static final String PRODUCT_JSON = "{\"name\":\"AdminTest\",\"price\":1,\"kind\":\"laptop\"}";

	@Autowired
	MockMvc mvc;

	@Autowired
	jakarta.persistence.EntityManager em;

	@Autowired
	AdminService adminService;

	/** 점검용 회원 한 명. */
	private record Account(long id, String loginId, String bearer) {
	}

	private Account admin;
	private Account user;

	@BeforeEach
	void setUp() throws Exception {
		admin = signup("pt_adm1", true);
		user = signup("pt_usr1", false);
	}

	/**
	 * 가입 후 로그인해 토큰을 받는다. admin이면 role을 ADMIN으로 바꾼다. 권한은 요청마다 DB의 현재 role을 읽으므로
	 * 토큰을 다시 받을 필요가 없다. 네이티브 UPDATE 뒤에는 영속성 컨텍스트를 비워 필터가 DB 값을 다시 읽게 한다.
	 */
	private Account signup(String loginId, boolean makeAdmin) throws Exception {
		String signupBody = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
				"{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"tester\",\"email\":\""
						+ loginId + "@example.com\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		long id = Long.parseLong(signupBody.replaceAll(".*\"member_id\":(\\d+).*", "$1"));
		if (makeAdmin) {
			em.createNativeQuery("update mall_member set role = 'ADMIN' where login_id = ?1").setParameter(1, loginId)
					.executeUpdate();
			em.flush();
			em.clear();
		}
		String loginBody = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = loginBody.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
		return new Account(id, loginId, "Bearer " + token);
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, Account as, String body) throws Exception {
		if (as != null) builder.header("Authorization", as.bearer());
		if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
		return mvc.perform(builder);
	}

	private ResultActions setRole(Account as, long targetId, String role) throws Exception {
		return send(put("/api/admin/members/" + targetId + "/role"), as, "{\"role\":\"" + role + "\"}");
	}

	private ResultActions ban(Account as, long targetId, String type, String reason, String until) throws Exception {
		String body = "{\"type\":\"" + type + "\"" + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
				+ (until == null ? "" : ",\"until\":\"" + until + "\"") + "}";
		return send(post("/api/admin/members/" + targetId + "/ban"), as, body);
	}

	private ResultActions unban(Account as, long targetId) throws Exception {
		return send(delete("/api/admin/members/" + targetId + "/ban"), as, null);
	}

	private ResultActions login(String loginId) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"));
	}

	/** T1: 토큰이 없으면 4개 API 모두 401, 일반 회원은 403이고 데이터는 그대로다. */
	@Test
	void adminApisRequireAdmin() throws Exception {
		for (Account as : new Account[] { null, user }) {
			int expected = as == null ? 401 : 403;
			send(get("/api/admin/members"), as, null).andExpect(status().is(expected));
			setRole(as, user.id(), "ADMIN").andExpect(status().is(expected));
			ban(as, admin.id(), "PERMANENT", "x", null).andExpect(status().is(expected));
			unban(as, user.id()).andExpect(status().is(expected));
		}
		send(get("/api/admin/members"), admin, null).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.login_id=='" + user.loginId() + "')].role").value("USER"));
	}

	/** T2: 목록은 민감 필드를 담지 않고 밴 회원은 BANNED로 나온다. */
	@Test
	void membersListHidesSecretsAndShowsBanStatus() throws Exception {
		ban(admin, user.id(), "7D", "spam", null).andExpect(status().isOk());
		String body = send(get("/api/admin/members"), admin, null).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		org.junit.jupiter.api.Assertions.assertFalse(body.contains("password"), "비밀번호 필드가 없어야 한다");
		org.junit.jupiter.api.Assertions.assertFalse(body.contains("token_version"), "토큰 버전이 없어야 한다");
		send(get("/api/admin/members"), admin, null)
				.andExpect(jsonPath("$[?(@.login_id=='" + user.loginId() + "')].status").value("BANNED"))
				.andExpect(jsonPath("$[?(@.login_id=='" + user.loginId() + "')].ban_reason").value("spam"))
				.andExpect(jsonPath("$[?(@.login_id=='" + admin.loginId() + "')].status").value("ACTIVE"));
	}

	/** T3: 승격하면 그 회원의 기존 토큰으로 상품 쓰기가 되고(재로그인 불필요), 해제하면 다시 403. */
	@Test
	void promotionTakesEffectWithoutRelogin() throws Exception {
		send(post("/api/products"), user, PRODUCT_JSON).andExpect(status().isForbidden());
		setRole(admin, user.id(), "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
		em.flush();
		em.clear();
		send(post("/api/products"), user, PRODUCT_JSON).andExpect(status().isCreated());
		setRole(admin, user.id(), "USER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
		em.flush();
		em.clear();
		send(post("/api/products"), user, PRODUCT_JSON).andExpect(status().isForbidden());
	}

	/** T4: 자기 자신 해제·밴과 ADMIN 대상 밴은 403. */
	@Test
	void selfAndAdminTargetsAreForbidden() throws Exception {
		setRole(admin, admin.id(), "USER").andExpect(status().isForbidden());
		ban(admin, admin.id(), "PERMANENT", "self", null).andExpect(status().isForbidden());
		Account other = signup("pt_adm2", true);
		ban(admin, other.id(), "PERMANENT", "peer", null).andExpect(status().isForbidden());
		send(get("/api/admin/members"), admin, null).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.login_id=='" + admin.loginId() + "')].role").value("ADMIN"))
				.andExpect(jsonPath("$[?(@.login_id=='" + other.loginId() + "')].status").value("ACTIVE"));
	}

	/** T5: 다른 ADMIN을 해제할 수 있고, ADMIN이 한 명만 남으면 서비스가 해제를 409로 거부한다. */
	@Test
	void lastAdminCannotBeDemoted() throws Exception {
		Account other = signup("pt_adm2", true);
		setRole(admin, other.id(), "USER").andExpect(status().isOk());
		setRole(admin, admin.id(), "USER").andExpect(status().isForbidden());

		// 이 트랜잭션 안에서만(롤백됨) 점검 관리자 외의 ADMIN을 USER로 내려 ADMIN을 admin 한 명으로 만든다.
		em.createNativeQuery("update mall_member set role = 'USER' where role = 'ADMIN' and login_id <> ?1")
				.setParameter(1, admin.loginId()).executeUpdate();
		em.flush();
		em.clear();
		ResponseStatusException e = assertThrows(ResponseStatusException.class,
				() -> adminService.changeRole(-1L, admin.id(), "USER"));
		assertEquals(409, e.getStatusCode().value());
	}

	/** T6: 기간·영구·직접 지정 밴은 200, 사유 없음·과거·type 오류는 400. 밴되면 로그인 403, 기존 토큰은 401. */
	@Test
	void banVariantsAndValidation() throws Exception {
		String future = LocalDateTime.now().plusDays(3).withNano(0).toString();
		String past = LocalDateTime.now().minusDays(1).withNano(0).toString();
		ban(admin, user.id(), "7D", "r1", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BANNED"))
				.andExpect(jsonPath("$.banned_permanent").value(false));
		ban(admin, user.id(), "PERMANENT", "r2", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.banned_permanent").value(true));
		ban(admin, user.id(), "UNTIL", "r3", future).andExpect(status().isOk())
				.andExpect(jsonPath("$.banned_permanent").value(false));
		ban(admin, user.id(), "1D", null, null).andExpect(status().isBadRequest());
		ban(admin, user.id(), "UNTIL", "r4", past).andExpect(status().isBadRequest());
		ban(admin, user.id(), "FOREVER", "r5", null).andExpect(status().isBadRequest());

		login(user.loginId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BANNED"));
		send(get("/api/auth/me"), user, null).andExpect(status().isUnauthorized());
	}

	/** T7: 기간 밴의 해제 시각이 지났으면 ACTIVE이고 로그인된다. */
	@Test
	void expiredBanIsActiveAgain() throws Exception {
		ban(admin, user.id(), "7D", "short", null).andExpect(status().isOk());
		em.createNativeQuery("update mall_member set banned_until = ?1 where login_id = ?2")
				.setParameter(1, LocalDateTime.now().minusMinutes(1)).setParameter(2, user.loginId()).executeUpdate();
		em.flush();
		em.clear();
		send(get("/api/admin/members"), admin, null)
				.andExpect(jsonPath("$[?(@.login_id=='" + user.loginId() + "')].status").value("ACTIVE"));
		login(user.loginId()).andExpect(status().isOk());
	}

	/** T8: 밴 해제 후 로그인된다. 밴이 아닌 회원의 해제도 200. */
	@Test
	void unbanRestoresLogin() throws Exception {
		ban(admin, user.id(), "PERMANENT", "r", null).andExpect(status().isOk());
		login(user.loginId()).andExpect(status().isForbidden());
		unban(admin, user.id()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		login(user.loginId()).andExpect(status().isOk());
		unban(admin, user.id()).andExpect(status().isOk());
	}

	/** T9: 밴 중인 회원 승격은 409, 없는 회원은 404, 잘못된 role은 400, 같은 역할은 멱등 200. */
	@Test
	void roleEdgeCases() throws Exception {
		ban(admin, user.id(), "PERMANENT", "r", null).andExpect(status().isOk());
		setRole(admin, user.id(), "ADMIN").andExpect(status().isConflict());
		unban(admin, user.id()).andExpect(status().isOk());
		setRole(admin, 999999999L, "ADMIN").andExpect(status().isNotFound());
		ban(admin, 999999999L, "PERMANENT", "r", null).andExpect(status().isNotFound());
		unban(admin, 999999999L).andExpect(status().isNotFound());
		setRole(admin, user.id(), "SUPER").andExpect(status().isBadRequest());
		setRole(admin, user.id(), "USER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
	}
}
