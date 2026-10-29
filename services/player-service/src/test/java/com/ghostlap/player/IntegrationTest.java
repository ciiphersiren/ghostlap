package com.ghostlap.player;

import com.ghostlap.player.player.PlayerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: the whole application with a real Postgres (Testcontainers),
 * called through MockMvc (real Spring MVC and security filters, no network socket).
 * Spring caches the context, so all test classes share one app and one container.
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
    protected PlayerRepository players;

    @BeforeEach
    void cleanDatabase() {
        players.deleteAll();
    }

    protected static String authJson(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    /** Registers a player and returns the token from the response. */
    protected String registerAndGetToken(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
