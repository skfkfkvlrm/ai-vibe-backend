package com.oraclejava.demodog.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** main application.properties의 actuator 설정이 의도대로 노출되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"management.endpoint.health.show-components=always",
		"management.endpoint.health.show-details=always",
		"management.endpoint.health.probes.enabled=true",
		"management.endpoint.health.probes.add-additional-paths=true",
		"management.endpoint.health.group.core.include=ping",
		"management.endpoint.health.group.core.show-details=never",
		"management.endpoint.health.group.ops.include=*",
		"management.endpoint.health.group.ops.show-details=always"
})
class ActuatorHealthTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void healthShowsDbAndDogStoreDetails() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"))
				.andExpect(jsonPath("$.components.dogStore.status").value("UP"))
				.andExpect(jsonPath("$.components.dogStore.details.table").value("dogs"))
				.andExpect(jsonPath("$.components.dogStore.details.dogCount").isNumber());
	}

	@Test
	void probesAreExposed() throws Exception {
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/livez")).andExpect(status().isOk());
		mvc.perform(get("/readyz")).andExpect(status().isOk());
	}

	@Test
	void coreGroupHidesDetails() throws Exception {
		mvc.perform(get("/actuator/health/core"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.ping.status").value("UP"))
				.andExpect(jsonPath("$.components.ping.details").doesNotExist())
				.andExpect(jsonPath("$.components.db").doesNotExist())
				.andExpect(jsonPath("$.components.dogStore").doesNotExist());
	}

	@Test
	void opsGroupShowsAllDetails() throws Exception {
		mvc.perform(get("/actuator/health/ops"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.dogStore.details.dogCount").isNumber());
	}
}
