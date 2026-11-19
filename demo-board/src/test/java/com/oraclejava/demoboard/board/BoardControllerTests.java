package com.oraclejava.demoboard.board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.jayway.jsonpath.JsonPath;

/** 실제 JPA 계층(H2, MySQL 호환 모드)까지 포함한 게시판 API 통합 테스트. */
@SpringBootTest
class BoardControllerTests {

	private static final String PASS = "pw1234";

	@Autowired
	private BoardController boardController;

	@Autowired
	private BoardRepository boardRepository;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		mvc = MockMvcBuilders.standaloneSetup(boardController).build();
	}

	private static String json(String name, String pass, String subject) {
		return "{\"board_name\":\"" + name + "\",\"board_pass\":\"" + pass
				+ "\",\"board_subject\":\"" + subject + "\",\"board_content\":\"내용\",\"board_file\":\"a.txt\"}";
	}

	/** 글/답글을 작성하고 응답의 board_num을 반환한다. */
	private long write(String url, String subject) throws Exception {
		String body = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
						.content(json("익명", PASS, subject)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.board_num")).longValue();
	}

	@Test
	void createRootPost() throws Exception {
		String body = mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON)
						.content(json("홍길동", PASS, "첫 글")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", Matchers.matchesPattern("/api/boards/[0-9]+")))
				.andExpect(jsonPath("$.board_name").value("홍길동"))
				.andExpect(jsonPath("$.board_subject").value("첫 글"))
				.andExpect(jsonPath("$.board_content").value("내용"))
				.andExpect(jsonPath("$.board_file").value("a.txt"))
				.andExpect(jsonPath("$.re_lev").value(0))
				.andExpect(jsonPath("$.re_step").value(0))
				.andExpect(jsonPath("$.board_readcount").value(0))
				.andExpect(jsonPath("$.board_date").exists())
				.andExpect(jsonPath("$.board_pass").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		long num = ((Number) JsonPath.read(body, "$.board_num")).longValue();
		long ref = ((Number) JsonPath.read(body, "$.re_ref")).longValue();
		Assertions.assertEquals(num, ref);

		String hash = boardRepository.findById(num).orElseThrow().getBoardPass();
		Assertions.assertNotEquals(PASS, hash);
		Assertions.assertTrue(hash.startsWith("$2"));
	}

	@Test
	void replyStructureAndOrder() throws Exception {
		long a = write("/api/boards", "A");
		long b = write("/api/boards/" + a + "/replies", "B");
		write("/api/boards/" + a + "/replies", "C");
		write("/api/boards/" + b + "/replies", "D");

		// A(lev 0, step 0), C(1, 1), B(1, 2), D(2, 3), 모두 같은 re_ref
		mvc.perform(get("/api/boards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(4))
				.andExpect(jsonPath("$.items[0].board_subject").value("A"))
				.andExpect(jsonPath("$.items[0].re_lev").value(0))
				.andExpect(jsonPath("$.items[0].re_step").value(0))
				.andExpect(jsonPath("$.items[1].board_subject").value("C"))
				.andExpect(jsonPath("$.items[1].re_lev").value(1))
				.andExpect(jsonPath("$.items[1].re_step").value(1))
				.andExpect(jsonPath("$.items[2].board_subject").value("B"))
				.andExpect(jsonPath("$.items[2].re_lev").value(1))
				.andExpect(jsonPath("$.items[2].re_step").value(2))
				.andExpect(jsonPath("$.items[3].board_subject").value("D"))
				.andExpect(jsonPath("$.items[3].re_lev").value(2))
				.andExpect(jsonPath("$.items[3].re_step").value(3))
				.andExpect(jsonPath("$.items[0].re_ref").value(a))
				.andExpect(jsonPath("$.items[1].re_ref").value(a))
				.andExpect(jsonPath("$.items[2].re_ref").value(a))
				.andExpect(jsonPath("$.items[3].re_ref").value(a));

		mvc.perform(post("/api/boards/999999/replies").contentType(MediaType.APPLICATION_JSON)
						.content(json("익명", PASS, "고아")))
				.andExpect(status().isNotFound());
	}

	@Test
	void newerRootAppearsFirst() throws Exception {
		write("/api/boards", "old");
		write("/api/boards", "new");
		mvc.perform(get("/api/boards"))
				.andExpect(jsonPath("$.items[0].board_subject").value("new"))
				.andExpect(jsonPath("$.items[1].board_subject").value("old"));
	}

	@Test
	void wrongPasswordIsForbidden() throws Exception {
		long id = write("/api/boards", "비밀글");
		String url = "/api/boards/" + id;

		mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json("익명", "wrong", "바꿈")))
				.andExpect(status().isForbidden());
		mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"board_pass\":\"wrong\",\"board_subject\":\"바꿈\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(delete(url).header("X-Board-Pass", "wrong"))
				.andExpect(status().isForbidden());

		mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json("수정자", PASS, "수정됨")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_name").value("수정자"))
				.andExpect(jsonPath("$.board_subject").value("수정됨"));

		mvc.perform(delete(url).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void patchUpdatesOnlyGivenFields() throws Exception {
		long id = write("/api/boards", "원래 제목");
		String url = "/api/boards/" + id;

		mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
						.content("{\"board_pass\":\"" + PASS + "\",\"board_content\":\"새 내용\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_content").value("새 내용"))
				.andExpect(jsonPath("$.board_subject").value("원래 제목"))
				.andExpect(jsonPath("$.board_name").value("익명"))
				.andExpect(jsonPath("$.board_file").value("a.txt"));

		mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
						.content("{\"board_pass\":\"" + PASS + "\",\"board_subject\":\" \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getIncreasesReadcount() throws Exception {
		long id = write("/api/boards", "조회수");
		String url = "/api/boards/" + id;

		mvc.perform(get(url)).andExpect(jsonPath("$.board_readcount").value(1));
		mvc.perform(get(url)).andExpect(jsonPath("$.board_readcount").value(2));
		// 목록 조회는 조회수를 올리지 않는다
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$.items[0].board_readcount").value(2));
	}

	@Test
	void readcountCannotBeSetByClient() throws Exception {
		mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON)
						.content("{\"board_name\":\"a\",\"board_pass\":\"p\",\"board_subject\":\"s\",\"board_readcount\":999,\"re_lev\":5}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.board_readcount").value(0))
				.andExpect(jsonPath("$.re_lev").value(0));
	}

	@Test
	void missingBoardReturns404() throws Exception {
		mvc.perform(get("/api/boards/999999")).andExpect(status().isNotFound());
		mvc.perform(put("/api/boards/999999").contentType(MediaType.APPLICATION_JSON).content(json("a", "p", "s")))
				.andExpect(status().isNotFound());
		mvc.perform(patch("/api/boards/999999").contentType(MediaType.APPLICATION_JSON).content("{\"board_pass\":\"p\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/boards/999999").header("X-Board-Pass", "p")).andExpect(status().isNotFound());
	}

	@Test
	void invalidInputReturns400() throws Exception {
		mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON).content(json(" ", "p", "s")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON).content(json("a", "", "s")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON).content("{\"board_name\":\"a\",\"board_pass\":\"p\"}"))
				.andExpect(status().isBadRequest());
		long id = write("/api/boards", "x");
		mvc.perform(delete("/api/boards/" + id)).andExpect(status().isBadRequest());
	}

	/** 삭제는 soft delete: 204, 목록에서 제외, DB 행은 남고 deleted_at이 기록된다. */
	@Test
	void deleteIsSoftDelete() throws Exception {
		long keep = write("/api/boards", "남는 글");
		long gone = write("/api/boards", "삭제될 글");

		mvc.perform(delete("/api/boards/" + gone).header("X-Board-Pass", PASS))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/boards"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].board_num").value(keep));
		Assertions.assertNotNull(boardRepository.findById(gone).orElseThrow().getDeletedAt());
		Assertions.assertNull(boardRepository.findById(keep).orElseThrow().getDeletedAt());
	}

	/** 삭제된 글은 조회·수정·삭제·답글 모두 404, 조회수도 오르지 않는다. */
	@Test
	void softDeletedPostIsNotFound() throws Exception {
		long id = write("/api/boards", "삭제될 글");
		String url = "/api/boards/" + id;
		mvc.perform(delete(url).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());

		mvc.perform(get(url)).andExpect(status().isNotFound());
		mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json("a", PASS, "s")))
				.andExpect(status().isNotFound());
		mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"board_pass\":\"" + PASS + "\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete(url).header("X-Board-Pass", PASS)).andExpect(status().isNotFound());
		mvc.perform(post(url + "/replies").contentType(MediaType.APPLICATION_JSON).content(json("a", PASS, "s")))
				.andExpect(status().isNotFound());
	}

	/** 답글이 있는 원글을 삭제해도 답글 행은 DB에 그대로 남는다(관리자 화면용 보존). */
	@Test
	void deletingRootKeepsReplyRows() throws Exception {
		long root = write("/api/boards", "원글");
		long reply = write("/api/boards/" + root + "/replies", "답글");

		mvc.perform(delete("/api/boards/" + root).header("X-Board-Pass", PASS))
				.andExpect(status().isNoContent());

		Assertions.assertEquals(2, boardRepository.count());
		Assertions.assertNull(boardRepository.findById(reply).orElseThrow().getDeletedAt());
	}
}
