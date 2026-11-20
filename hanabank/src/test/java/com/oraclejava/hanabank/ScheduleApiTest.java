package com.oraclejava.hanabank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleApiTest {
    @Autowired MockMvc mvc;

    @Test
    void returnsSeededSchedulesInDateOrder() throws Exception {
        mvc.perform(get("/api/hanabank/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].team").value("삼성생명"))
            .andExpect(jsonPath("$[1].game").value("0"));
    }

    @Test
    void createUpdateDelete() throws Exception {
        String body = "{\"gdate\":\"2027-01-05\",\"game\":\"1\",\"team\":\"KB스타즈\"}";
        String res = mvc.perform(post("/api/hanabank/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.team").value("KB스타즈"))
            .andReturn().getResponse().getContentAsString();
        String id = res.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(put("/api/hanabank/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gdate\":\"2027-01-06\",\"game\":\"0\",\"team\":\"KB스타즈\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.game").value("0"));

        mvc.perform(delete("/api/hanabank/schedules/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/hanabank/schedules/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/hanabank/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gdate\":\"abc\",\"game\":\"1\",\"team\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/hanabank/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gdate\":\"2027-01-05\",\"game\":\"2\",\"team\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }
}
