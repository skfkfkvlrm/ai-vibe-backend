package com.oraclejava.demoboard.board;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 로그인 회원 소유권 + 비회원 비밀번호 방식 병행 규칙을 보안 필터까지 포함해 검증한다. */
@SpringBootTest
class BoardOwnershipTests {

	private static final String PASS = "pw1234";
	private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private BoardRepository boardRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private MemberService memberService;
	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mvc;
	private MemberEntity owner;
	private MemberEntity other;
	private String ownerAuth;
	private String otherAuth;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		memberService.signup("owner_01", "pw123456", "소유자", "owner@example.com");
		memberService.signup("other_01", "pw123456", "타인", "other@example.com");
		owner = memberRepository.findByLoginId("owner_01").orElseThrow();
		other = memberRepository.findByLoginId("other_01").orElseThrow();
		ownerAuth = "Bearer " + jwtProvider.issue(owner);
		otherAuth = "Bearer " + jwtProvider.issue(other);
	}

	private static String memberBody(String subject) {
		// 로그인 상태에서는 이름·비밀번호를 보내지 않아도 된다. 보낸 이름은 무시된다.
		return "{\"board_name\":\"무시되는 이름\",\"board_subject\":\"" + subject + "\",\"board_content\":\"내용\"}";
	}

	private static String guestBody(String subject) {
		return "{\"board_name\":\"손님\",\"board_pass\":\"" + PASS + "\",\"board_subject\":\"" + subject
				+ "\",\"board_content\":\"내용\"}";
	}

	private long create(String body, String auth) throws Exception {
		var req = post("/api/boards").contentType(JSON).content(body);
		if (auth != null) req.header("Authorization", auth);
		String res = mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(res, "$.board_num")).longValue();
	}

	/** 회원 글: member_id와 회원 이름이 저장되고, 본인은 비밀번호 없이 수정·삭제, 타인 403, 비로그인 401. */
	@Test
	void memberPostIsOwnedByTheMember() throws Exception {
		mvc.perform(post("/api/boards").contentType(JSON).content(memberBody("내 글")).header("Authorization", ownerAuth))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.member_id").value(owner.getMemberId()))
				.andExpect(jsonPath("$.board_name").value("소유자"));

		long id = boardRepository.findAll().get(0).getBoardNum();
		String url = "/api/boards/" + id;
		String patchBody = "{\"board_subject\":\"수정됨\"}";

		mvc.perform(patch(url).contentType(JSON).content(patchBody)).andExpect(status().isUnauthorized());
		mvc.perform(patch(url).contentType(JSON).content(patchBody).header("Authorization", otherAuth))
				.andExpect(status().isForbidden());
		mvc.perform(delete(url).header("Authorization", otherAuth)).andExpect(status().isForbidden());
		mvc.perform(delete(url)).andExpect(status().isUnauthorized());
		// 비밀번호를 안다고 해서 비회원이 회원 글을 고칠 수는 없다.
		mvc.perform(delete(url).header("X-Board-Pass", PASS)).andExpect(status().isUnauthorized());

		mvc.perform(patch(url).contentType(JSON).content(patchBody).header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_subject").value("수정됨"))
				.andExpect(jsonPath("$.board_name").value("소유자"));
		mvc.perform(delete(url).header("Authorization", ownerAuth)).andExpect(status().isNoContent());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	/** 비회원 글(기존 글 포함): 비밀번호 방식 그대로, 로그인 회원도 남의 비회원 글은 비밀번호가 필요하다. */
	@Test
	void guestPostKeepsPasswordRule() throws Exception {
		long id = create(guestBody("손님 글"), null);
		String url = "/api/boards/" + id;
		mvc.perform(get("/api/boards")).andExpect(jsonPath("$[0].member_id").value(Matchers.nullValue()));

		String noPass = "{\"board_subject\":\"수정됨\"}";
		String wrongPass = "{\"board_pass\":\"wrong\",\"board_subject\":\"수정됨\"}";
		String goodPass = "{\"board_pass\":\"" + PASS + "\",\"board_subject\":\"수정됨\"}";

		// 로그인 여부와 무관하게 비밀번호 규칙이 적용된다.
		mvc.perform(patch(url).contentType(JSON).content(noPass).header("Authorization", otherAuth))
				.andExpect(status().isBadRequest());
		mvc.perform(patch(url).contentType(JSON).content(wrongPass).header("Authorization", otherAuth))
				.andExpect(status().isForbidden());
		mvc.perform(patch(url).contentType(JSON).content(goodPass).header("Authorization", otherAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.board_subject").value("수정됨"))
				.andExpect(jsonPath("$.board_name").value("손님"));
		mvc.perform(delete(url).header("X-Board-Pass", "wrong")).andExpect(status().isForbidden());
		mvc.perform(delete(url).header("X-Board-Pass", PASS)).andExpect(status().isNoContent());
	}

	/** 답글도 같은 규칙: 로그인 상태의 답글은 작성자에게 귀속되고, 비로그인 작성은 필수값 검증을 유지한다. */
	@Test
	void replyOwnershipAndGuestValidation() throws Exception {
		long root = create(guestBody("원글"), null);

		mvc.perform(post("/api/boards/" + root + "/replies").contentType(JSON).content(memberBody("답글"))
						.header("Authorization", otherAuth))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.member_id").value(other.getMemberId()))
				.andExpect(jsonPath("$.board_name").value("타인"))
				.andExpect(jsonPath("$.re_lev").value(1));

		// 비로그인은 이름·비밀번호·제목이 모두 필요하다.
		mvc.perform(post("/api/boards").contentType(JSON).content(memberBody("비밀번호 없음")))
				.andExpect(status().isBadRequest());
		Assertions.assertEquals(2, boardRepository.count());
	}
}
