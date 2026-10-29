package com.ghostlap.race.web;

import com.ghostlap.race.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class RequestIdFilterTest extends IntegrationTest {

    @Test
    void everyResponseGetsARequestId() throws Exception {
        String id = mvc.perform(get("/api/v1/tracks/1/leaderboard"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id).matches("[0-9a-f-]{36}"); // a fresh UUID
    }

    @Test
    void errorsGetARequestIdToo() throws Exception {
        // 401 from Spring Security: the filter runs before it.
        mvc.perform(post("/api/v1/laps"))
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void callersRequestIdIsKept() throws Exception {
        mvc.perform(get("/api/v1/tracks/1/leaderboard").header(RequestIdFilter.HEADER, "game-client-42"))
                .andExpect(header().string(RequestIdFilter.HEADER, "game-client-42"));
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        String id = mvc.perform(get("/api/v1/tracks/1/leaderboard").header(RequestIdFilter.HEADER, "bad id\nFAKE LOG LINE"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id).matches("[0-9a-f-]{36}");
    }
}
