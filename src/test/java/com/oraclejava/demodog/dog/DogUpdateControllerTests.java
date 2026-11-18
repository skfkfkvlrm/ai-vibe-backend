package com.oraclejava.demodog.dog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** PUT /api/update/{id} 통합 테스트 (H2, MySQL 호환 모드). */
@SpringBootTest
class DogUpdateControllerTests {

	@Autowired
	private DogUpdateController controller;

	@Autowired
	private DogService dogService;

	@Autowired
	private DogRepository dogRepository;

	private MockMvc mvc;
	private long id;

	@BeforeEach
	void setUp() {
		dogRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(controller).build();
		id = dogService.create(new DogRequest("진돗개", 500000, "jindo.jpg", "대한민국", 50.5, 18.2, "원본 설명")).id();
	}

	@Test
	void updateReplacesAllFieldsAndClearsOmittedOnes() throws Exception {
		mvc.perform(put("/api/update/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"시바견\",\"price\":800000,\"image\":\"uploads/a.webp\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.kind").value("시바견"))
				.andExpect(jsonPath("$.price").value(800000))
				.andExpect(jsonPath("$.image").value("uploads/a.webp"));

		Dog saved = dogRepository.findById(id).orElseThrow().toDog();
		Assertions.assertEquals("시바견", saved.kind());
		Assertions.assertNull(saved.country(), "PUT은 전체 교체라 빠진 항목은 null이 된다");
		Assertions.assertNull(saved.height());
		Assertions.assertNull(saved.content());
	}

	@Test
	void updateDoesNotTouchReadcount() throws Exception {
		dogService.readAndCount(id);
		dogService.readAndCount(id);
		mvc.perform(put("/api/update/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"진돗개\",\"readcount\":999}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readcount").value(2));
	}

	@Test
	void blankKindReturns400() throws Exception {
		mvc.perform(put("/api/update/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\" \"}"))
				.andExpect(status().isBadRequest());
		Assertions.assertEquals("진돗개", dogRepository.findById(id).orElseThrow().toDog().kind());
	}

	@Test
	void missingIdReturns404() throws Exception {
		mvc.perform(put("/api/update/999999").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void nonJsonContentTypeReturns415() throws Exception {
		mvc.perform(put("/api/update/" + id).contentType(MediaType.TEXT_PLAIN).content("kind=x"))
				.andExpect(status().isUnsupportedMediaType());
	}
}
