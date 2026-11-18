package com.oraclejava.demodog.dog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** POST /api/add 통합 테스트 (H2, MySQL 호환 모드). */
@SpringBootTest
class DogAddControllerTests {

	@Autowired
	private DogAddController controller;

	@Autowired
	private DogRepository dogRepository;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		dogRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void addCreatesDogAndReturnsLocation() throws Exception {
		mvc.perform(post("/api/add").contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"시바견\",\"price\":800000,\"image\":\"uploads/a.webp\",\"height\":38.5}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", Matchers.startsWith("/api/dogs/")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.kind").value("시바견"))
				.andExpect(jsonPath("$.image").value("uploads/a.webp"))
				.andExpect(jsonPath("$.readcount").value(0));
		Assertions.assertEquals(1, dogRepository.count());
	}

	@Test
	void missingOrBlankKindReturns400() throws Exception {
		mvc.perform(post("/api/add").contentType(MediaType.APPLICATION_JSON).content("{\"price\":100}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/add").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"  \"}"))
				.andExpect(status().isBadRequest());
		Assertions.assertEquals(0, dogRepository.count());
	}

	@Test
	void negativePriceReturns400() throws Exception {
		mvc.perform(post("/api/add").contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"x\",\"price\":-1}"))
				.andExpect(status().isBadRequest());
		Assertions.assertEquals(0, dogRepository.count());
	}

	@Test
	void nonJsonContentTypeReturns415() throws Exception {
		mvc.perform(post("/api/add").contentType(MediaType.TEXT_PLAIN).content("kind=x"))
				.andExpect(status().isUnsupportedMediaType());
	}
}
