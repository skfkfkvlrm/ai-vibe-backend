package com.oraclejava.pc_mall.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
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

	@Test
	void unknownIdReturns404() throws Exception {
		mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
	}
}
