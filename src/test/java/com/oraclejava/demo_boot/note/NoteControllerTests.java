package com.oraclejava.demo_boot.note;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실제 JPA 계층(H2, MySQL 호환 모드)까지 포함한 노트 API 통합 테스트. */
@SpringBootTest
class NoteControllerTests {

	@Autowired
	private NoteController noteController;

	@Autowired
	private NoteRepository noteRepository;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		noteRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(noteController).build();
	}

	/** 노트를 생성하고 Location 헤더에서 경로(/api/notes/{id})를 돌려준다. */
	private String createNote(String json) throws Exception {
		return mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}

	@Test
	void crudLifecycle() throws Exception {
		String location = mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"첫 노트\",\"content\":\"내용\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/notes/[0-9]+")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("첫 노트"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andReturn().getResponse().getHeader("Location");

		mvc.perform(get("/api/notes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("내용"));

		mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"수정됨\",\"content\":\"새 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("수정됨"));

		mvc.perform(delete(location)).andExpect(status().isNoContent());
		mvc.perform(get(location)).andExpect(status().isNotFound());
	}

	@Test
	void patchUpdatesOnlyGivenFields() throws Exception {
		String location = createNote("{\"title\":\"원래 제목\",\"content\":\"원래 내용\"}");

		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"바뀐 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("원래 제목"))
				.andExpect(jsonPath("$.content").value("바뀐 내용"));

		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"바뀐 제목\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("바뀐 제목"))
				.andExpect(jsonPath("$.content").value("바뀐 내용"));

		mvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\" \"}"))
				.andExpect(status().isBadRequest());

		mvc.perform(patch("/api/notes/999999").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void listReturnsNewestFirst() throws Exception {
		createNote("{\"title\":\"먼저\",\"content\":\"1\"}");
		createNote("{\"title\":\"나중\",\"content\":\"2\"}");

		mvc.perform(get("/api/notes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("나중"))
				.andExpect(jsonPath("$[1].title").value("먼저"));
	}

	@Test
	void missingNoteReturns404() throws Exception {
		mvc.perform(get("/api/notes/999999")).andExpect(status().isNotFound());
		mvc.perform(put("/api/notes/999999").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/notes/999999")).andExpect(status().isNotFound());
	}

	@Test
	void blankTitleReturns400() throws Exception {
		mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"  \",\"content\":\"내용\"}"))
				.andExpect(status().isBadRequest());
	}
}
