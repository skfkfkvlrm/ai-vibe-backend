package com.oraclejava.demodog.dog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** soft delete(/api/delete, 기존 /api/dogs 삭제 포함)와 복구(/api/restore) 통합 테스트. */
@SpringBootTest
class DogSoftDeleteTests {

	@Autowired
	private DogController dogController;

	@Autowired
	private DogUpdateController updateController;

	@Autowired
	private DogDeleteController deleteController;

	@Autowired
	private DogRestoreController restoreController;

	@Autowired
	private DogDeletedController deletedController;

	@Autowired
	private DogService dogService;

	@Autowired
	private DogRepository dogRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private long id;

	@BeforeEach
	void setUp() {
		dogRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(dogController, updateController, deleteController, restoreController,
				deletedController)
				.build();
		id = dogService.create(new DogRequest("진돗개", 500000, "jindo.jpg", "대한민국", 50.5, 18.2, "설명")).id();
	}

	private int deletedRows(long dogId) {
		return jdbc.queryForObject("select count(*) from dogs where id = ? and deleted_at is not null",
				Integer.class, dogId);
	}

	@Test
	void deleteHidesTheDogEverywhereButKeepsTheRow() throws Exception {
		mvc.perform(delete("/api/delete/" + id)).andExpect(status().isNoContent());

		Assertions.assertEquals(1, deletedRows(id), "행은 남고 deleted_at만 채워져야 한다");
		Assertions.assertTrue(dogService.findAll().stream().noneMatch(d -> d.id() == id));
		Assertions.assertEquals(0, dogRepository.count(), "헬스 지표가 쓰는 count()도 삭제 행을 제외해야 한다");
		mvc.perform(get("/api/dogs/" + id)).andExpect(status().isNotFound());
		mvc.perform(put("/api/update/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"x\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/delete/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void deletedDogDoesNotGetReadcountIncreased() {
		dogService.delete(id);
		Assertions.assertTrue(dogService.readAndCount(id).isEmpty());
		Assertions.assertEquals(1, deletedRows(id));
	}

	@Test
	void missingIdReturns404() throws Exception {
		mvc.perform(delete("/api/delete/999999")).andExpect(status().isNotFound());
	}

	@Test
	void legacyDeleteEndpointIsAlsoSoft() throws Exception {
		mvc.perform(delete("/api/dogs/" + id)).andExpect(status().isNoContent());
		Assertions.assertEquals(1, deletedRows(id), "기존 DELETE /api/dogs/{id}도 행을 지우면 안 된다");
		mvc.perform(get("/api/dogs/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void restoreBringsTheDogBack() throws Exception {
		mvc.perform(delete("/api/delete/" + id)).andExpect(status().isNoContent());

		mvc.perform(post("/api/restore/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.kind").value("진돗개"));

		Assertions.assertEquals(0, deletedRows(id));
		Assertions.assertTrue(dogService.findAll().stream().anyMatch(d -> d.id() == id));
	}

	@Test
	void deletedListShowsOnlyDeletedDogsAndDropsRestoredOnes() throws Exception {
		long other = dogService.create(new DogRequest("말티즈", 300000, null, null, null, null, null)).id();
		dogService.delete(id);

		mvc.perform(get("/api/deleted"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id==" + id + ")].kind").value("진돗개"))
				.andExpect(jsonPath("$[?(@.id==" + id + ")].price").value(500000))
				.andExpect(jsonPath("$[?(@.id==" + id + ")].deletedAt").isNotEmpty())
				.andExpect(jsonPath("$[?(@.id==" + other + ")]").isEmpty());

		mvc.perform(post("/api/restore/" + id)).andExpect(status().isOk());

		mvc.perform(get("/api/deleted"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
	}

	@Test
	void deletedListIsOrderedByMostRecentlyDeletedFirst() {
		long second = dogService.create(new DogRequest("말티즈", 300000, null, null, null, null, null)).id();
		jdbc.update("update dogs set deleted_at = ? where id = ?", java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), id);
		jdbc.update("update dogs set deleted_at = ? where id = ?", java.sql.Timestamp.valueOf("2020-01-02 00:00:00"), second);

		var ids = dogService.findDeleted().stream().map(DeletedDog::id).toList();
		Assertions.assertTrue(ids.indexOf(second) < ids.indexOf(id), "최근 삭제한 개가 먼저 와야 한다");
	}

	@Test
	void restoreOfNonDeletedOrMissingDogReturns404() throws Exception {
		mvc.perform(post("/api/restore/" + id)).andExpect(status().isNotFound());
		mvc.perform(post("/api/restore/999999")).andExpect(status().isNotFound());
	}
}
