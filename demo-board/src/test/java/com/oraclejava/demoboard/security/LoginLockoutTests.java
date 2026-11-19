package com.oraclejava.demoboard.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Assertions;
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

import com.oraclejava.demoboard.board.BoardRepository;
import com.oraclejava.demoboard.member.MemberRepository;
import com.oraclejava.demoboard.member.MemberService;

/** 로그인 실패 제한: 계정+IP 기준 연속 5회 실패 시 5분 잠금, 잠금 사실은 429로 알리고 감사 로그에 남긴다. */
@SpringBootTest
class LoginLockoutTests {

	private static final String PASS = "pw123456";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private BoardRepository boardRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private MemberService memberService;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from login_attempt");
		jdbc.update("delete from admin_audit_log");
		boardRepository.deleteAll();
		memberRepository.deleteAll();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		memberService.signup("user_01", PASS, "회원", "user@example.com");
	}

	private ResultActions login(String loginId, String password, String ip) throws Exception {
		return mvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + password + "\"}")
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				}));
	}

	private void fail(String loginId, int times, String ip) throws Exception {
		for (int i = 0; i < times; i++) {
			login(loginId, "wrong-pass-1", ip).andExpect(status().isUnauthorized());
		}
	}

	/** T1: 5회 실패 후에는 올바른 비밀번호도 429. 다른 IP는 영향 없고, 없는 아이디도 똑같이 잠겨 존재 여부가 드러나지 않는다. */
	@Test
	void locksAfterFiveFailuresPerAccountAndIp() throws Exception {
		fail("user_01", 5, "10.0.0.1");

		login("user_01", PASS, "10.0.0.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
				.andExpect(jsonPath("$.retry_after_seconds").isNumber());

		// 다른 IP에서는 그대로 로그인된다(계정+IP 단위 잠금).
		login("user_01", PASS, "10.0.0.2").andExpect(status().isOk());

		// 존재하지 않는 아이디도 5회 실패 후 같은 429
		fail("nobody_01", 5, "10.0.0.1");
		login("nobody_01", "wrong-pass-1", "10.0.0.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

		// 잠금 발생은 아이디별로 1건씩 감사 로그에 남고, 비밀번호는 기록되지 않는다.
		Assertions.assertEquals(2, jdbc.queryForObject(
				"select count(*) from admin_audit_log where action = 'LOGIN_LOCKED' and admin_id is null", Integer.class));
		Assertions.assertEquals(0, jdbc.queryForObject(
				"select count(*) from admin_audit_log where detail like '%wrong-pass%' or detail like '%" + PASS + "%'",
				Integer.class));
	}

	/** 잠금 시간이 지나면 다시 로그인되고, 성공하면 실패 횟수가 초기화된다. */
	@Test
	void lockExpiresAndSuccessResetsCounter() throws Exception {
		fail("user_01", 5, "10.0.0.1");
		jdbc.update("update login_attempt set locked_until = ? where login_id = 'user_01'", LocalDateTime.now().minusSeconds(1));

		login("user_01", PASS, "10.0.0.1").andExpect(status().isOk());
		Assertions.assertEquals(0, jdbc.queryForObject(
				"select coalesce(sum(failed_count), 0) from login_attempt where login_id = 'user_01'", Integer.class));

		// 초기화되었으므로 4회 더 실패해도 잠기지 않는다.
		fail("user_01", 4, "10.0.0.1");
		login("user_01", PASS, "10.0.0.1").andExpect(status().isOk());
	}
}
