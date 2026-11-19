package com.oraclejava.pc_mall.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTests {

	@Autowired
	MockMvc mvc;

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

	@Test
	void createThenGetReturnsSameValues() throws Exception {
		String body = mvc.perform(multipart("/api/products").file(new MockMultipartFile("pic_s", "s.png", "image/png", PNG))
				.param("name", "ThinkPad T14").param("price", "450000").param("kind", "laptop").param("text", "used"))
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
				.param("name", "A").param("price", "1").param("kind", "laptop")).andReturn().getResponse()
				.getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(multipart(HttpMethod.PUT, "/api/products/" + id).param("name", "B").param("price", "2")
				.param("kind", "laptop")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("B"))
				.andExpect(jsonPath("$.hasPicL").value(true));
	}

	private static String json(String extra) {
		return "{\"name\":\"JsonBook\",\"price\":1000,\"kind\":\"laptop\"" + extra + "}";
	}

	private static String b64() {
		return java.util.Base64.getEncoder().encodeToString(PNG);
	}

	@Test
	void jsonCreateStoresBase64ImageAsBinary() throws Exception {
		String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
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
		String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content(json(",\"pic_l\":\"" + b64() + "\""))).andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renamed\",\"price\":2,\"kind\":\"laptop\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.hasPicL").value(true));
	}

	@Test
	void jsonInvalidBase64Returns400() throws Exception {
		mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content(json(",\"pic_s\":\"@@not-base64@@\""))).andExpect(status().isBadRequest());
	}

	@Autowired
	jakarta.persistence.EntityManager em;

	@Test
	void deleteIsSoftDeleteHiddenFromReadsButRowKept() throws Exception {
		String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json("")))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());
		em.flush();
		em.clear();

		mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/products")).andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
		mvc.perform(delete("/api/products/" + id)).andExpect(status().isNotFound());
		Object deletedAt = em.createNativeQuery("select deleted_at from product where id = ?1")
				.setParameter(1, Long.parseLong(id)).getSingleResult();
		org.junit.jupiter.api.Assertions.assertNotNull(deletedAt, "행은 남고 deleted_at이 채워져야 한다");
	}

	@Test
	void unknownIdReturns404() throws Exception {
		mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
	}
}
