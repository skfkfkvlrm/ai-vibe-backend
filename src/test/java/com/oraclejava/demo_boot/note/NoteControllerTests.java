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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NoteControllerTests {

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new NoteController(new NoteService())).build();
	}

	@Test
	void crudLifecycle() throws Exception {
		mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"첫 노트\",\"content\":\"내용\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/notes/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.title").value("첫 노트"));

		mvc.perform(get("/api/notes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(get("/api/notes/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("내용"));

		mvc.perform(put("/api/notes/1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"수정됨\",\"content\":\"새 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("수정됨"));

		mvc.perform(delete("/api/notes/1")).andExpect(status().isNoContent());
		mvc.perform(get("/api/notes/1")).andExpect(status().isNotFound());
	}

	@Test
	void patchUpdatesOnlyGivenFields() throws Exception {
		mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"원래 제목\",\"content\":\"원래 내용\"}"))
				.andExpect(status().isCreated());

		mvc.perform(patch("/api/notes/1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"바뀐 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("원래 제목"))
				.andExpect(jsonPath("$.content").value("바뀐 내용"));

		mvc.perform(patch("/api/notes/1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"바뀐 제목\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("바뀐 제목"))
				.andExpect(jsonPath("$.content").value("바뀐 내용"));

		mvc.perform(patch("/api/notes/1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\" \"}"))
				.andExpect(status().isBadRequest());

		mvc.perform(patch("/api/notes/99").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void missingNoteReturns404() throws Exception {
		mvc.perform(get("/api/notes/99")).andExpect(status().isNotFound());
		mvc.perform(put("/api/notes/99").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"x\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/notes/99")).andExpect(status().isNotFound());
	}

	@Test
	void blankTitleReturns400() throws Exception {
		mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"  \",\"content\":\"내용\"}"))
				.andExpect(status().isBadRequest());
	}
}
