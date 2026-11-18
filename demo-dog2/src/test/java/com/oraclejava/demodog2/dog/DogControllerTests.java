package com.oraclejava.demodog2.dog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 JPA 계층(H2, MySQL 호환 모드)까지 포함한 개 API 통합 테스트. */
@SpringBootTest
class DogControllerTests {

	private static final String JINDO = """
			{"kind":"진돗개","price":500000,"image":"jindo.jpg","country":"대한민국",
			 "height":50.5,"weight":18.2,"content":"충성심이 강한 한국 토종견"}
			""";

	@Autowired
	private DogController dogController;

	@Autowired
	private DogRepository dogRepository;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		dogRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(dogController).build();
	}

	private String createDog(String json) throws Exception {
		return mvc.perform(post("/api/dogs").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}

	@Test
	void crudLifecycle() throws Exception {
		String location = mvc.perform(post("/api/dogs").contentType(MediaType.APPLICATION_JSON).content(JINDO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", Matchers.matchesPattern("/api/dogs/[0-9]+")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.kind").value("진돗개"))
				.andExpect(jsonPath("$.price").value(500000))
				.andExpect(jsonPath("$.country").value("대한민국"))
				.andExpect(jsonPath("$.height").value(50.5))
				.andExpect(jsonPath("$.weight").value(18.2))
				.andExpect(jsonPath("$.readcount").value(0))
				.andReturn().getResponse().getHeader("Location");

		mvc.perform(get("/api/dogs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("충성심이 강한 한국 토종견"));

		mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
						.content("{\"kind\":\"풍산개\",\"price\":700000,\"country\":\"북한\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("풍산개"))
				.andExpect(jsonPath("$.image").doesNotExist())
				.andExpect(jsonPath("$.readcount").value(1));

		mvc.perform(delete(location)).andExpect(status().isNoContent());
		mvc.perform(get(location)).andExpect(status().isNotFound());
	}

	@Test
	void getIncreasesReadcount() throws Exception {
		String location = createDog(JINDO);

		mvc.perform(get(location)).andExpect(jsonPath("$.readcount").value(1));
		mvc.perform(get(location)).andExpect(jsonPath("$.readcount").value(2));
		// 목록 조회는 조회수를 올리지 않는다
		mvc.perform(get("/api/dogs")).andExpect(jsonPath("$[0].readcount").value(2));
	}

	@Test
	void readcountCannotBeSetByClient() throws Exception {
		String location = createDog("{\"kind\":\"시바\",\"readcount\":999}");
		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"readcount\":555}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readcount").value(0));
	}

	@Test
	void patchUpdatesOnlyGivenFields() throws Exception {
		String location = createDog(JINDO);

		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"price\":450000}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(450000))
				.andExpect(jsonPath("$.kind").value("진돗개"))
				.andExpect(jsonPath("$.image").value("jindo.jpg"));

		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\" \"}"))
				.andExpect(status().isBadRequest());

		mvc.perform(patch("/api/dogs/999999").contentType(MediaType.APPLICATION_JSON).content("{\"price\":1}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void missingDogReturns404() throws Exception {
		mvc.perform(get("/api/dogs/999999")).andExpect(status().isNotFound());
		mvc.perform(put("/api/dogs/999999").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"x\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/dogs/999999")).andExpect(status().isNotFound());
	}

	@Test
	void invalidInputReturns400() throws Exception {
		mvc.perform(post("/api/dogs").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/dogs").contentType(MediaType.APPLICATION_JSON).content("{\"price\":100}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/dogs").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"x\",\"weight\":-1}"))
				.andExpect(status().isBadRequest());
	}
}
