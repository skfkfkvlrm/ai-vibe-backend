package com.oraclejava.pc_mall.order;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.oraclejava.pc_mall.product.Product;
import com.oraclejava.pc_mall.product.ProductRepository;

/** 장바구니 → 주문폼 → 모의 결제 흐름. 각 테스트는 롤백된다. */
@SpringBootTest
@Transactional
class CartOrderApiTests {

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private ProductRepository products;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private String login(String loginId) throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
				"{\"login_id\":\"" + loginId + "\",\"password\":\"pw123456\",\"name\":\"회원\",\"email\":\"" + loginId
						+ "@example.com\"}"))
				.andExpect(status().isCreated());
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login_id\":\"" + loginId + "\",\"password\":\"pw123456\"}")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + body.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
	}

	private static final String FORM = "{\"formName\":\"홍길동\",\"formMail\":\"hong@example.com\",\"formMsg\":\"문 앞에\"}";

	@Test
	void requiresLogin() throws Exception {
		mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(FORM))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cartToOrderToPay() throws Exception {
		String auth = login("qa_cart01");
		Long pid = products.save(new Product("테스트 키보드", 10000, "KEYBOARD", null)).getId();

		// 같은 상품을 두 번 담으면 수량이 합쳐진다
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/cart").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":" + pid + ",\"quantity\":2}")).andExpect(status().isOk());
		}
		mvc.perform(get("/api/cart").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].quantity").value(4))
				.andExpect(jsonPath("$.totalPrice").value(40000));

		// 주문폼 제출 → 주문 생성(ORDERED), 장바구니 비움
		String order = mvc.perform(post("/api/orders").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(FORM)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ORDERED")).andExpect(jsonPath("$.totalPrice").value(40000))
				.andExpect(jsonPath("$.formName").value("홍길동")).andExpect(jsonPath("$.lines[0].quantity").value(4))
				.andReturn().getResponse().getContentAsString();
		String orderId = order.replaceAll(".*?\"id\":(\\d+).*", "$1");
		mvc.perform(get("/api/cart").header("Authorization", auth)).andExpect(jsonPath("$.items.length()").value(0));

		// 모의 결제
		mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
		mvc.perform(get("/api/orders").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(1));

		// 다른 회원은 이 주문에 접근할 수 없다
		String other = login("qa_cart02");
		mvc.perform(get("/api/orders/" + orderId).header("Authorization", other)).andExpect(status().isNotFound());
		mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", other))
				.andExpect(status().isNotFound());
	}

	@Test
	void emptyCartAndInvalidInputAreRejected() throws Exception {
		String auth = login("qa_cart03");
		mvc.perform(post("/api/orders").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(FORM)).andExpect(status().isBadRequest());
		Long pid = products.save(new Product("마우스", 5000, "MOUSE", null)).getId();
		mvc.perform(post("/api/cart").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + pid + ",\"quantity\":0}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/cart").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + pid + ",\"quantity\":1}")).andExpect(status().isOk());
		mvc.perform(post("/api/orders").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"formName\":\"\",\"formMail\":\"x\",\"formMsg\":null}")).andExpect(status().isBadRequest());
	}
}
