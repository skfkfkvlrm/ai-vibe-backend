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

/** 주문 부분/전체 취소 및 모의 환불 API 테스트. 각 테스트는 롤백된다. */
@SpringBootTest
@Transactional
class OrderCancelApiTests {

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private ProductRepository products;
	@Autowired
	private OrderRepository orders;

	private MockMvc mvc;

	private static final String FORM = "{\"formName\":\"홍길동\",\"formMail\":\"hong@example.com\",\"formMsg\":\"문 앞에\"}";

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

	private record PaidOrder(Long orderId, Long lineId, int unitPrice) {
	}

	private PaidOrder createPaidOrder(String auth, int unitPrice, int quantity) throws Exception {
		Long pid = products.save(new Product("취소 테스트 상품", unitPrice, "KEYBOARD", null)).getId();
		mvc.perform(post("/api/cart").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + pid + ",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
		String body = mvc.perform(post("/api/orders").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(FORM)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long orderId = Long.valueOf(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
		mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
		Long lineId = orders.findById(orderId).orElseThrow().getLines().get(0).getId();
		return new PaidOrder(orderId, lineId, unitPrice);
	}

	private static String cancelBody(String reason, Long lineId, int quantity) {
		return "{\"reason\":\"" + reason + "\",\"items\":[{\"lineId\":" + lineId + ",\"quantity\":" + quantity + "}]}";
	}

	@Test
	void partialCancelThenExceedThenFullCancelAndConflict() throws Exception {
		String auth = login("qa_cancel01");
		PaidOrder paid = createPaidOrder(auth, 15000, 2);

		// (a) PAID 주문 수량 2 중 1 취소 → PARTIALLY_CANCELED, 환불액 단가x1, canceledQuantity 1
		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("단순 변심", paid.lineId(), 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_CANCELED"))
				.andExpect(jsonPath("$.refundedTotal").value(15000))
				.andExpect(jsonPath("$.lines[0].canceledQuantity").value(1));

		// (b) 남은 수량(1) 초과 취소 → 400
		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("초과 취소", paid.lineId(), 2)))
				.andExpect(status().isBadRequest());

		// (d) 남은 수량 전부 취소 → CANCELED, 한 번 더 취소 → 409
		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("나머지 취소", paid.lineId(), 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELED"))
				.andExpect(jsonPath("$.refundedTotal").value(30000))
				.andExpect(jsonPath("$.lines[0].canceledQuantity").value(2));

		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("중복 취소", paid.lineId(), 1)))
				.andExpect(status().isConflict());
	}

	@Test
	void otherMemberCannotCancel() throws Exception {
		String owner = login("qa_cancel02");
		String other = login("qa_cancel03");
		PaidOrder paid = createPaidOrder(owner, 10000, 2);

		// (c) 다른 회원이 취소 요청 → 404
		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", other)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("타인 취소", paid.lineId(), 1)))
				.andExpect(status().isNotFound());
	}

	@Test
	void emptyReasonIsRejected() throws Exception {
		String auth = login("qa_cancel04");
		PaidOrder paid = createPaidOrder(auth, 10000, 2);

		// (e) 사유가 비어 있으면 → 400
		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("   ", paid.lineId(), 1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unitPriceSnapshotPreservedAfterProductPriceChange() throws Exception {
		String auth = login("qa_cancel05");
		PaidOrder paid = createPaidOrder(auth, 15000, 2);

		Product product = orders.findById(paid.orderId()).orElseThrow().getLines().get(0).getProduct();
		product.update(product.getName(), 99000, product.getKind(), product.getText());
		products.saveAndFlush(product);

		mvc.perform(get("/api/orders/" + paid.orderId()).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].price").value(15000));

		mvc.perform(post("/api/orders/" + paid.orderId() + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("단가 스냅샷 확인", paid.lineId(), 2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refundedTotal").value(30000));
	}

	@Test
	void partialCancelBeforePayUpdatesPayableAmountAndKeepsItOnPay() throws Exception {
		String auth = login("qa_cancel06");
		Long pid = products.save(new Product("결제전 취소 테스트 상품", 12000, "MOUSE", null)).getId();
		mvc.perform(post("/api/cart").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + pid + ",\"quantity\":2}")).andExpect(status().isOk());
		String body = mvc.perform(post("/api/orders").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(FORM)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long orderId = Long.valueOf(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
		Long lineId = orders.findById(orderId).orElseThrow().getLines().get(0).getId();

		mvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(cancelBody("결제 전 일부 취소", lineId, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canceledAmount").value(12000))
				.andExpect(jsonPath("$.payableAmount").value(12000))
				.andExpect(jsonPath("$.cancels.length()").value(1))
				.andExpect(jsonPath("$.cancels[0].reason").value("결제 전 일부 취소"));

		mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.payableAmount").value(12000));
	}
}
