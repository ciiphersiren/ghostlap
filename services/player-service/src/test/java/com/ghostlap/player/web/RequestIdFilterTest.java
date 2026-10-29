package com.ghostlap.player.web;

import com.ghostlap.player.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class RequestIdFilterTest extends IntegrationTest {

    @Test
    void everyResponseGetsARequestId() throws Exception {
        // A 401 (no token): the filter runs before Spring Security, so errors get an ID too.
        String id = mvc.perform(get("/api/v1/players/me"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id).matches("[0-9a-f-]{36}"); // a fresh UUID
    }

    @Test
    void callersRequestIdIsKept() throws Exception {
        mvc.perform(get("/api/v1/players/me").header(RequestIdFilter.HEADER, "game-client-42"))
                .andExpect(header().string(RequestIdFilter.HEADER, "game-client-42"));
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        String id = mvc.perform(get("/api/v1/players/me").header(RequestIdFilter.HEADER, "bad id\nFAKE LOG LINE"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id).matches("[0-9a-f-]{36}");
    }
}
