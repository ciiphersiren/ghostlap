package com.ghostlap.race;

import com.ghostlap.race.lap.LapGhostRepository;
import com.ghostlap.race.lap.LapRepository;
import com.ghostlap.race.lap.LapSubmissionRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: the whole application with a real Postgres and Redis
 * (Testcontainers), called through MockMvc (real Spring MVC and security filters, no network socket).
 * Spring caches the context, so all test classes share one app and one pair of containers.
 */
@SpringBootTest(properties = "ghostlap.jwt.secret=" + IntegrationTest.TEST_JWT_SECRET)
@AutoConfigureMockMvc
@AutoConfigureMetrics // tests turn metrics export off by default; we test /actuator/prometheus
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String TEST_JWT_SECRET = "test-only-secret-that-is-at-least-32-bytes";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected LapRepository laps;

    @Autowired
    protected LapGhostRepository ghosts;

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    void cleanDatabases() {
        ghosts.deleteAll();
        laps.deleteAll();
        redis.delete(redis.keys("leaderboard:*"));
    }

    protected ResultActions submit(String token, LapSubmissionRequest lap) throws Exception {
        return mvc.perform(post("/api/v1/laps")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(lap)));
    }

    /** Submits a lap that must be accepted and returns its lapId. */
    protected long submitAccepted(long playerId, String username, int lapTimeMs) throws Exception {
        String body = submit(TestTokens.forPlayer(playerId, username), TestLaps.validLap(lapTimeMs))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number lapId = JsonPath.read(body, "$.lapId");
        return lapId.longValue();
    }
}
