package com.oraclejava.demoboard.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberEntity;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 경로별 공개·인증 규칙과 토큰 처리(무효 토큰은 비로그인으로 취급)를 검증한다. */
@SpringBootTest
class SecurityRulesTests {

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
	private MemberEntity member;

	@BeforeEach
	void setUp() {
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		memberService.signup("user_01", "pw123456", "회원", "user@example.com");
		member = memberRepository.findByLoginId("user_01").orElseThrow();
	}

	/** 조회·헬스는 로그인 없이 공개, 비회원 글쓰기도 허용(비밀번호는 서비스가 검사). */
	@Test
	void publicEndpointsAreOpen() throws Exception {
		mvc.perform(get("/api/boards")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON)
						.content("{\"board_name\":\"손님\",\"board_pass\":\"pw1234\",\"board_subject\":\"제목\"}"))
				.andExpect(status().isCreated());
	}

	/** /me와 규칙에 없는 경로는 토큰이 없으면 401, 유효한 토큰이면 /me는 200. */
	@Test
	void protectedEndpointsNeedToken() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/unknown-area")).andExpect(status().isUnauthorized());

		String token = jwtProvider.issue(member);
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	/** 형식이 틀리거나 만료·위조된 토큰은 오류 대신 비로그인으로 취급해 공개 API는 그대로 동작한다. */
	@Test
	void invalidTokenIsTreatedAsAnonymous() throws Exception {
		String expired = jwtProvider.issue(member.getMemberId(), "USER", Duration.ofSeconds(-60));
		for (String bad : new String[] { "Bearer not.a.jwt", "Bearer " + expired, "Basic abc" }) {
			mvc.perform(get("/api/boards").header("Authorization", bad)).andExpect(status().isOk());
			mvc.perform(get("/api/auth/me").header("Authorization", bad)).andExpect(status().isUnauthorized());
		}
	}
}
