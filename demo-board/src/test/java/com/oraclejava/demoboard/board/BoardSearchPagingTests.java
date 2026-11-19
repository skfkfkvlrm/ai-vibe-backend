package com.oraclejava.demoboard.board;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

/** 목록 페이징(원글 기준, 답글 동봉)·검색(제목+내용/작성자)·조회수 증가 억제(count=false). */
@SpringBootTest
class BoardSearchPagingTests {

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

	private long write(String url, String name, String subject, String content) throws Exception {
		String body = "{\"board_name\":\"" + name + "\",\"board_pass\":\"" + PASS + "\",\"board_subject\":\"" + subject
				+ "\",\"board_content\":\"" + content + "\"}";
		String res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	private long root(String subject) throws Exception {
		return write("/api/boards", "작성자", subject, "내용");
	}

	/** T1: 원글 25개 → 원글 기준 10개씩, 답글은 해당 원글 바로 뒤에 동봉, 다른 페이지 원글의 답글은 제외. */
	@Test
	void pagesByRootPostsAndAttachesTheirReplies() throws Exception {
		long[] ids = new long[26];
		for (int i = 1; i <= 25; i++) {
			ids[i] = root(String.format("글 %02d", i));
		}
		write("/api/boards/" + ids[25] + "/replies", "작성자", "글25 답글", "내용");
		write("/api/boards/" + ids[3] + "/replies", "작성자", "글03 답글1", "내용");
		write("/api/boards/" + ids[3] + "/replies", "작성자", "글03 답글2", "내용");

		mvc.perform(get("/api/boards?page=0&size=10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.total_elements").value(25))
				.andExpect(jsonPath("$.total_pages").value(3))
				.andExpect(jsonPath("$.items.length()").value(11))
				.andExpect(jsonPath("$.items[0].board_subject").value("글 25"))
				.andExpect(jsonPath("$.items[1].board_subject").value("글25 답글"))
				.andExpect(jsonPath("$.items[1].re_lev").value(1))
				.andExpect(jsonPath("$.items[2].board_subject").value("글 24"))
				.andExpect(jsonPath("$.items[?(@.board_subject=='글03 답글1')]").isEmpty());

		mvc.perform(get("/api/boards?page=2&size=10"))
				.andExpect(jsonPath("$.items.length()").value(7))
				.andExpect(jsonPath("$.items[?(@.re_lev==0)]").value(hasSize(5)))
				.andExpect(jsonPath("$.items[?(@.board_subject=='글03 답글1')]").isNotEmpty())
				.andExpect(jsonPath("$.items[?(@.board_subject=='글03 답글2')]").isNotEmpty())
				.andExpect(jsonPath("$.items[?(@.board_subject=='글25 답글')]").isEmpty());

		mvc.perform(get("/api/boards?page=1&size=20"))
				.andExpect(jsonPath("$.total_pages").value(2))
				.andExpect(jsonPath("$.items.length()").value(5 + 2)); // 글 05~01 + 글03 답글 2개

		// 기본값은 0페이지·10개, 범위를 넘으면 빈 목록(건수는 유지)
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.page").value(0));
		mvc.perform(get("/api/boards?page=9&size=10"))
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.total_elements").value(25));

		// 삭제된 원글은 목록·건수에서 빠진다.
		mvc.perform(delete("/api/boards/" + ids[24]).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());
		mvc.perform(get("/api/boards?page=0&size=10"))
				.andExpect(jsonPath("$.total_elements").value(24))
				.andExpect(jsonPath("$.items[?(@.board_subject=='글 24')]").isEmpty());

		// 허용되지 않는 크기·음수 페이지·숫자가 아닌 값은 400
		for (String q : new String[] { "size=15", "size=0", "size=100", "page=-1", "page=abc" }) {
			mvc.perform(get("/api/boards?" + q)).andExpect(status().isBadRequest());
		}
	}

	/** T2: 제목+내용(대소문자 무시)·작성자 검색, 답글만 일치해도 원글 노출, % _ 는 글자 그대로, 삭제 글 제외, 잘못된 입력 400. */
	@Test
	void searchesByTitleContentOrAuthor() throws Exception {
		write("/api/boards", "kim", "Spring 입문", "x");
		write("/api/boards", "lee", "리액트", "내용에 SPRING 포함");
		write("/api/boards", "홍길동", "무관", "무관");
		long r4 = write("/api/boards", "park", "원글4", "c");
		write("/api/boards/" + r4 + "/replies", "choi", "답글", "keyword-only-in-reply");
		write("/api/boards", "han", "100% 할인", "c");
		write("/api/boards", "han", "1000 할인", "c");
		write("/api/boards", "han", "a_b", "c");
		write("/api/boards", "han", "aXb", "c");
		long deleted = write("/api/boards", "han", "spring 삭제됨", "c");
		mvc.perform(delete("/api/boards/" + deleted).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());

		// 제목·내용 일치, 대소문자 무시, 최신 원글 먼저, 삭제된 글 제외
		mvc.perform(get("/api/boards?q=spring"))
				.andExpect(jsonPath("$.total_elements").value(2))
				.andExpect(jsonPath("$.items[0].board_subject").value("리액트"))
				.andExpect(jsonPath("$.items[1].board_subject").value("Spring 입문"));
		mvc.perform(get("/api/boards?field=TITLE_CONTENT&q=SPRING")).andExpect(jsonPath("$.total_elements").value(2));

		// 답글만 일치해도 원글이 나오고 답글이 함께 붙는다.
		mvc.perform(get("/api/boards?q=keyword-only-in-reply"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].board_subject").value("원글4"))
				.andExpect(jsonPath("$.items[1].re_lev").value(1));

		// 긴 본문(255자 초과)의 뒷부분에 있는 검색어도 찾는다(cast로 잘리지 않는지 확인).
		write("/api/boards", "long", "긴 본문", "가".repeat(400) + "tail-keyword");
		mvc.perform(get("/api/boards?q=tail-keyword"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].board_subject").value("긴 본문"));

		// 작성자 검색(부분 일치). 제목에만 있는 단어는 작성자 검색에 걸리지 않는다.
		mvc.perform(get("/api/boards?field=AUTHOR&q=길동"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].board_subject").value("무관"));
		mvc.perform(get("/api/boards?field=AUTHOR&q=spring")).andExpect(jsonPath("$.total_elements").value(0));
		// 답글 작성자만 일치해도 원글이 나온다.
		mvc.perform(get("/api/boards?field=AUTHOR&q=choi"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].board_subject").value("원글4"));

		// % 와 _ 는 와일드카드가 아니라 글자 그대로 검색한다.
		mvc.perform(get("/api/boards").param("q", "100%"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].board_subject").value("100% 할인"));
		mvc.perform(get("/api/boards").param("q", "a_b"))
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].board_subject").value("a_b"));

		// 결과가 없으면 빈 목록·0건
		mvc.perform(get("/api/boards?q=존재하지않는단어"))
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.total_elements").value(0))
				.andExpect(jsonPath("$.total_pages").value(0));

		// 잘못된 조건·너무 긴 검색어는 400
		mvc.perform(get("/api/boards?field=FOO&q=a")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/boards").param("q", "a".repeat(101))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/boards").param("q", "a".repeat(100))).andExpect(status().isOk());
	}

	/** T4: 검색어가 비었거나 공백이면 검색 조건 없이 전체 페이징과 같다. */
	@Test
	void blankQueryMeansNoFilter() throws Exception {
		root("가");
		root("나");
		mvc.perform(get("/api/boards?q=")).andExpect(jsonPath("$.total_elements").value(2));
		mvc.perform(get("/api/boards").param("q", "   ")).andExpect(jsonPath("$.total_elements").value(2));
	}

	/** T3: count=false는 조회수를 올리지 않고, 기본 호출은 1 올린다. 삭제된 글은 두 경우 모두 404. */
	@Test
	void countFalseDoesNotIncreaseReadcount() throws Exception {
		long id = root("조회수");
		mvc.perform(get("/api/boards/" + id + "?count=false")).andExpect(jsonPath("$.board_readcount").value(0));
		mvc.perform(get("/api/boards/" + id + "?count=false")).andExpect(jsonPath("$.board_readcount").value(0));
		mvc.perform(get("/api/boards/" + id)).andExpect(jsonPath("$.board_readcount").value(1));
		mvc.perform(get("/api/boards/" + id + "?count=false")).andExpect(jsonPath("$.board_readcount").value(1));
		Assertions.assertEquals(1, boardRepository.findById(id).orElseThrow().toBoard().boardReadcount());

		mvc.perform(delete("/api/boards/" + id).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());
		mvc.perform(get("/api/boards/" + id + "?count=false")).andExpect(status().isNotFound());
		mvc.perform(get("/api/boards/" + id)).andExpect(status().isNotFound());
	}
}
