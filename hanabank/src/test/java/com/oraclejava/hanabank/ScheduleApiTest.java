package com.oraclejava.hanabank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleApiTest {
    @Autowired MockMvc mvc;

    @Test
    void returnsSeededSchedulesInDateOrder() throws Exception {
        mvc.perform(get("/api/hanabank/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].team").value("삼성생명"))
            .andExpect(jsonPath("$[1].game").value("0"));
    }
}
