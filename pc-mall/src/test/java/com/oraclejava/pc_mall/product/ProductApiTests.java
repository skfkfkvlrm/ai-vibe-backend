package com.oraclejava.pc_mall.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 상품 API. 조회는 공개, 쓰기(POST·PUT·DELETE)는 ADMIN 전용이다. 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	jakarta.persistence.EntityManager em;

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };
	private static final String PASSWORD = "pw123456";

	private String admin;
	private String user;

	@BeforeEach
	void setUp() throws Exception {
		admin = "Bearer " + signupAndLogin("pt_admin", "pt_admin@example.com");
		// 권한은 요청마다 DB의 현재 role을 읽으므로 가입 후 role만 바꾸면 이 토큰이 ADMIN으로 통한다(롤백됨).
		em.createNativeQuery("update mall_member set role = 'ADMIN' where login_id = 'pt_admin'").executeUpdate();
		em.flush();
		// 네이티브 UPDATE는 영속성 컨텍스트에 캐시된 엔티티(role=USER)를 갱신하지 않으므로 비워 DB 값을 다시 읽게 한다.
		em.clear();
		user ="Bearer " + signupAndLogin("pt_user", "pt_user@example.com");
	}

	private String signupAndLogin(String loginId, String email) throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
				"{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"tester\",\"email\":\""
						+ email + "\"}")).andExpect(status().isCreated());
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
	}

	private static String json(String extra) {
		return "{\"name\":\"JsonBook\",\"price\":1000,\"kind\":\"laptop\"" + extra + "}";
	}

	private static String b64() {
		return java.util.Base64.getEncoder().encodeToString(PNG);
	}

	/** ADMIN으로 JSON 등록하고 id를 돌려준다. */
	private String createAsAdmin() throws Exception {
		String body = mvc.perform(post("/api/products").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content(json(""))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\":(\\d+).*", "$1");
	}

	@Test
	void createThenGetReturnsSameValues() throws Exception {
		String body = mvc.perform(multipart("/api/products").file(new MockMultipartFile("pic_s", "s.png", "image/png", PNG))
				.header("Authorization", admin).param("name", "ThinkPad T14").param("price", "450000")
				.param("kind", "laptop").param("text", "used"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("ThinkPad T14")).andExpect(jsonPath("$.price").value(450000))
				.andExpect(jsonPath("$.hasPicS").value(true)).andExpect(jsonPath("$.hasPicL").value(false));
		mvc.perform(get("/api/products/" + id + "/pic-s")).andExpect(status().isOk());
	}

	@Test
	void updateWithoutImagesKeepsExistingPics() throws Exception {
		String body = mvc.perform(multipart("/api/products").file(new MockMultipartFile("pic_l", "l.png", "image/png", PNG))
				.header("Authorization", admin).param("name", "A").param("price", "1").param("kind", "laptop"))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(multipart(HttpMethod.PUT, "/api/products/" + id).header("Authorization", admin)
				.param("name", "B").param("price", "2").param("kind", "laptop")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("B")).andExpect(jsonPath("$.hasPicL").value(true));
	}

	@Test
	void jsonCreateStoresBase64ImageAsBinary() throws Exception {
		String body = mvc.perform(post("/api/products").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(",\"pic_s\":\"data:image/png;base64," + b64() + "\"")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.hasPicS").value(true))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		byte[] stored = mvc.perform(get("/api/products/" + id + "/pic-s")).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsByteArray();
		org.junit.jupiter.api.Assertions.assertArrayEquals(PNG, stored);
	}

	@Test
	void jsonUpdateWithoutImagesKeepsExistingPics() throws Exception {
		String body = mvc.perform(post("/api/products").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content(json(",\"pic_l\":\"" + b64() + "\"")))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(put("/api/products/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renamed\",\"price\":2,\"kind\":\"laptop\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.hasPicL").value(true));
	}

	@Test
	void jsonInvalidBase64Returns400() throws Exception {
		mvc.perform(post("/api/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content(json(",\"pic_s\":\"@@not-base64@@\""))).andExpect(status().isBadRequest());
	}

	@Test
	void deleteIsSoftDeleteHiddenFromReadsButRowKept() throws Exception {
		String id = createAsAdmin();

		mvc.perform(delete("/api/products/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
		em.flush();
		em.clear();

		mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/products")).andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
		mvc.perform(delete("/api/products/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
		Object deletedAt = em.createNativeQuery("select deleted_at from product where id = ?1")
				.setParameter(1, Long.parseLong(id)).getSingleResult();
		org.junit.jupiter.api.Assertions.assertNotNull(deletedAt, "행은 남고 deleted_at이 채워져야 한다");
	}

	@Test
	void unknownIdReturns404() throws Exception {
		mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
	}

	/** 쓰기 5종(JSON 등록, multipart 등록, JSON 수정, multipart 수정, 삭제)을 주어진 Authorization(null이면 생략)으로 호출한다. */
	private void assertAllWritesReturn(int expected, String authorization, String id) throws Exception {
		var statusMatcher = status().is(expected);
		var jsonCreate = post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json(""));
		var multipartCreate = multipart("/api/products").param("name", "X").param("price", "1").param("kind", "laptop");
		var jsonUpdate = put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON).content(json(""));
		var multipartUpdate = multipart(HttpMethod.PUT, "/api/products/" + id).param("name", "X").param("price", "1")
				.param("kind", "laptop");
		var remove = delete("/api/products/" + id);
		if (authorization != null) {
			jsonCreate.header("Authorization", authorization);
			multipartCreate.header("Authorization", authorization);
			jsonUpdate.header("Authorization", authorization);
			multipartUpdate.header("Authorization", authorization);
			remove.header("Authorization", authorization);
		}
		mvc.perform(jsonCreate).andExpect(statusMatcher);
		mvc.perform(multipartCreate).andExpect(statusMatcher);
		mvc.perform(jsonUpdate).andExpect(statusMatcher);
		mvc.perform(multipartUpdate).andExpect(statusMatcher);
		mvc.perform(remove).andExpect(statusMatcher);
	}

	/** T1: 토큰 없는 쓰기는 모두 401. */
	@Test
	void writesWithoutTokenReturn401() throws Exception {
		assertAllWritesReturn(401, null, createAsAdmin());
	}

	/** T2: 일반 회원의 쓰기는 모두 403이고 데이터는 그대로다. */
	@Test
	void writesByPlainUserReturn403AndKeepData() throws Exception {
		String id = createAsAdmin();
		assertAllWritesReturn(403, user, id);
		mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("JsonBook"));
	}

	/** T4: 조회는 토큰 없이도 모두 200. */
	@Test
	void readsStayPublic() throws Exception {
		String body = mvc.perform(post("/api/products").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(",\"pic_s\":\"" + b64() + "\",\"pic_l\":\"" + b64() + "\"")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");
		mvc.perform(get("/api/products")).andExpect(status().isOk());
		mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());
		mvc.perform(get("/api/products/" + id + "/pic-s")).andExpect(status().isOk());
		mvc.perform(get("/api/products/" + id + "/pic-l")).andExpect(status().isOk());
	}

	/** T5: 변조된 토큰은 비로그인과 같아 쓰기가 401. */
	@Test
	void writesWithTamperedTokenReturn401() throws Exception {
		String token = admin.substring("Bearer ".length());
		int dot = token.indexOf('.');
		char c = token.charAt(dot + 2);
		String tampered = token.substring(0, dot + 2) + (c == 'A' ? 'B' : 'A') + token.substring(dot + 3);
		assertAllWritesReturn(401, "Bearer " + tampered, createAsAdmin());
	}
}
