package com.ghostlap.player.auth;

import com.ghostlap.player.IntegrationTest;
import com.ghostlap.player.player.Player;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegrationTest {

    @Test
    void registerCreatesPlayerAndReturnsToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("reet", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(notNullValue()))
                .andExpect(jsonPath("$.token").value(notNullValue()));

        assertThat(players.findByUsernameIgnoreCase("reet")).isPresent();
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        registerAndGetToken("reet", "password123");

        Player player = players.findByUsernameIgnoreCase("reet").orElseThrow();
        assertThat(player.getPasswordHash())
                .isNotEqualTo("password123")
                .startsWith("$2"); // BCrypt hashes look like $2a$10$<salt><hash>
    }

    @Test
    void duplicateUsernameIsConflictIgnoringCase() throws Exception {
        registerAndGetToken("reet", "password123");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("REET", "otherpassword")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Username 'REET' is already taken"));
    }

    @Test
    void invalidRegistrationIsBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("a b", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.username").value(notNullValue()))
                .andExpect(jsonPath("$.errors.password").value(notNullValue()));
    }

    @Test
    void loginReturnsTokenForCorrectPassword() throws Exception {
        registerAndGetToken("reet", "password123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("reet", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(notNullValue()));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        registerAndGetToken("reet", "password123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("reet", "wrongpassword")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void loginWithUnknownUserGivesTheSameError() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("nobody", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }
}
