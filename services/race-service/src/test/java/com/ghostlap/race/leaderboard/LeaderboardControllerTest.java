package com.ghostlap.race.leaderboard;

import com.ghostlap.race.IntegrationTest;
import com.ghostlap.race.TestLaps;
import com.ghostlap.race.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaderboardControllerTest extends IntegrationTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Test
    void playersAreOrderedFastestFirst() throws Exception {
        long slowLap = submitAccepted(1, "slow", 17_000);
        long fastLap = submitAccepted(2, "fast", 14_000);
        submitAccepted(3, "middle", 15_500);

        // No token: leaderboards are public.
        mvc.perform(get("/api/v1/tracks/1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].username").value("fast"))
                .andExpect(jsonPath("$[0].lapTimeMs").value(14_000))
                .andExpect(jsonPath("$[0].lapId").value(fastLap))
                .andExpect(jsonPath("$[1].username").value("middle"))
                .andExpect(jsonPath("$[2].username").value("slow"))
                .andExpect(jsonPath("$[2].lapId").value(slowLap));
    }

    @Test
    void onlyEachPlayersBestLapCounts() throws Exception {
        submitAccepted(1, "reet", 16_000);
        long best = submitAccepted(1, "reet", 15_000);
        submitAccepted(1, "reet", 15_800); // slower than the best: ignored

        mvc.perform(get("/api/v1/tracks/1/leaderboard"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lapTimeMs").value(15_000))
                .andExpect(jsonPath("$[0].lapId").value(best));
    }

    @Test
    void rejectedLapsNeverAppear() throws Exception {
        submit(TestTokens.forPlayer(1, "cheater"), TestLaps.validLap(5_000));

        mvc.perform(get("/api/v1/tracks/1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void limitControlsHowManyRowsAreReturned() throws Exception {
        submitAccepted(1, "a", 14_000);
        submitAccepted(2, "b", 15_000);
        submitAccepted(3, "c", 16_000);

        mvc.perform(get("/api/v1/tracks/1/leaderboard").param("limit", "2"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].username").value("b"));
    }

    @Test
    void limitOutOfRangeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/tracks/1/leaderboard").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tracks/1/leaderboard").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rowWithoutDetailsIsSkippedInsteadOfFailing() throws Exception {
        submitAccepted(1, "reet", 15_000);
        // What a reader could see mid-update before the writes were made transactional.
        redis.opsForZSet().add("leaderboard:track:1", "999", 14_000);

        mvc.perform(get("/api/v1/tracks/1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("reet"))
                .andExpect(jsonPath("$[0].rank").value(2)); // its real position on the board
    }

    @Test
    void unknownTrackIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/tracks/99/leaderboard"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rebuildRestoresTheBoardFromPostgres() throws Exception {
        submitAccepted(1, "slow", 17_000);
        submitAccepted(2, "fast", 14_000);
        submitAccepted(2, "fast", 14_500); // not a best
        submit(TestTokens.forPlayer(3, "cheater"), TestLaps.validLap(5_000)); // rejected
        String before = mvc.perform(get("/api/v1/tracks/1/leaderboard")).andReturn().getResponse().getContentAsString();

        redis.delete(redis.keys("leaderboard:*")); // Redis lost everything
        int players = leaderboardService.rebuild(1);

        assertThat(players).isEqualTo(2);
        String after = mvc.perform(get("/api/v1/tracks/1/leaderboard")).andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before);
    }
}
