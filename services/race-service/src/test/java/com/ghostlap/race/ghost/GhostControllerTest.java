package com.ghostlap.race.ghost;

import com.ghostlap.race.IntegrationTest;
import com.ghostlap.race.TestLaps;
import com.ghostlap.race.TestTokens;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GhostControllerTest extends IntegrationTest {

    @Test
    void topGhostIsTheFastestAcceptedLap() throws Exception {
        submitAccepted(1, "slow", 16_000);
        long fastLap = submitAccepted(2, "fast", 14_000);
        submit(TestTokens.forPlayer(3, "cheater"), TestLaps.validLap(5_000)); // rejected, must not win

        String body = mvc.perform(get("/api/v1/tracks/1/ghosts/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lapId").value(fastLap))
                .andExpect(jsonPath("$.username").value("fast"))
                .andExpect(jsonPath("$.lapTimeMs").value(14_000))
                .andReturn().getResponse().getContentAsString();

        // The ghost comes back byte-for-byte and decodes with the shared codec.
        Ghost ghost = GhostCodec.decode(Base64.getDecoder().decode((String) JsonPath.read(body, "$.ghost")));
        assertThat(ghost.lapTimeMs()).isEqualTo(14_000);
        assertThat(ghost.sampleCount()).isEqualTo(TestLaps.expectedSamples(14_000));
    }

    @Test
    void topGhostIsNotFoundWhenTrackHasNoLaps() throws Exception {
        mvc.perform(get("/api/v1/tracks/1/ghosts/top"))
                .andExpect(status().isNotFound());
    }

    @Test
    void topGhostOfUnknownTrackIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/tracks/99/ghosts/top"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lapGhostReturnsThatLapsGhost() throws Exception {
        long lapId = submitAccepted(1, "reet", 15_000);

        mvc.perform(get("/api/v1/laps/{lapId}/ghost", lapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lapId").value(lapId))
                .andExpect(jsonPath("$.trackId").value(1))
                .andExpect(jsonPath("$.ghost").value(TestLaps.validLap(15_000).ghost()));
    }

    @Test
    void ghostOfRejectedLapIsNotFound() throws Exception {
        String body = submit(TestTokens.forPlayer(1, "cheater"), TestLaps.validLap(5_000))
                .andReturn().getResponse().getContentAsString();
        Number lapId = JsonPath.read(body, "$.lapId");

        mvc.perform(get("/api/v1/laps/{lapId}/ghost", lapId.longValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ghostOfUnknownLapIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/laps/123456/ghost"))
                .andExpect(status().isNotFound());
    }
}
