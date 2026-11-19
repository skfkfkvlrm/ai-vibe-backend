package com.oraclejava.demoboard.board;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.oraclejava.demoboard.member.JwtProvider;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;
import com.oraclejava.demoboard.security.WriteRateLimitService;

/** 비회원 쓰기 속도 제한: IP당 글 3분에 1개, 답글 1분에 3개. 로그인 회원은 대상이 아니다. */
@SpringBootTest
class GuestWriteRateLimitTests {

	private static final String GUEST_POST = "{\"board_name\":\"손님\",\"board_pass\":\"guest-pass\",\"board_subject\":\"글\"}";

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
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private WriteRateLimitService rateLimit;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		// 테스트 설정은 속도 제한을 끄므로 이 클래스에서만 켠다.
		rateLimit.setEnabled(true);
		jdbc.update("delete from write_rate_limit");
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@AfterEach
	void tearDown() {
		rateLimit.setEnabled(false);
	}

	private ResultActions write(String url, String body, String ip, String auth) throws Exception {
		var builder = post(url).contentType(MediaType.APPLICATION_JSON).content(body).with(request -> {
			request.setRemoteAddr(ip);
			return request;
		});
		if (auth != null) builder.header("Authorization", auth);
		return mvc.perform(builder);
	}

	/** T3-a: 같은 IP의 비회원 글은 3분에 1개. 다른 IP, 로그인 회원은 영향 없고, 잘못된 요청은 한도를 쓰지 않는다. */
	@Test
	void guestPostsAreLimitedToOnePerThreeMinutesPerIp() throws Exception {
		write("/api/boards", "{\"board_subject\":\"\"}", "10.0.0.1", null).andExpect(status().isBadRequest());
		write("/api/boards", GUEST_POST, "10.0.0.1", null).andExpect(status().isCreated());
		write("/api/boards", GUEST_POST, "10.0.0.1", null)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.retry_after_seconds").isNumber());

		write("/api/boards", GUEST_POST, "10.0.0.2", null).andExpect(status().isCreated());

		memberService.signup("user_01", "pw123456", "회원", "user@example.com");
		String auth = "Bearer " + jwtProvider.issue(memberRepository.findByLoginId("user_01").orElseThrow());
		String member = "{\"board_subject\":\"회원 글\"}";
		write("/api/boards", member, "10.0.0.1", auth).andExpect(status().isCreated());
		write("/api/boards", member, "10.0.0.1", auth).andExpect(status().isCreated());

		// 3분이 지나면 다시 쓸 수 있다.
		jdbc.update("update write_rate_limit set created_at = ?", LocalDateTime.now().minusMinutes(4));
		write("/api/boards", GUEST_POST, "10.0.0.1", null).andExpect(status().isCreated());
	}

	/** T3-b: 같은 IP의 비회원 답글은 1분에 3개, 4번째부터 429. 글 한도와 따로 센다. */
	@Test
	void guestRepliesAreLimitedToThreePerMinutePerIp() throws Exception {
		String res = write("/api/boards", GUEST_POST, "10.0.0.9", null).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long root = ((Number) JsonPath.read(res, "$.board_num")).longValue();
		String reply = "{\"board_name\":\"손님\",\"board_pass\":\"guest-pass\",\"board_subject\":\"답글\"}";

		for (int i = 0; i < 3; i++) {
			write("/api/boards/" + root + "/replies", reply, "10.0.0.1", null).andExpect(status().isCreated());
		}
		write("/api/boards/" + root + "/replies", reply, "10.0.0.1", null)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		// 같은 IP라도 글 한도는 아직 남아 있다.
		write("/api/boards", GUEST_POST, "10.0.0.1", null).andExpect(status().isCreated());
	}
}
