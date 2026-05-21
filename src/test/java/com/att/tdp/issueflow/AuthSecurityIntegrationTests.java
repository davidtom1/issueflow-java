package com.att.tdp.issueflow;

import com.att.tdp.issueflow.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingAuthorizationHeaderOnProtectedRouteReturns401ErrorResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/auth/me"))
                .andReturn();

        ErrorResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.error()).isNotBlank();
        assertThat(response.message()).isNotBlank();
        assertThat(response.path()).isEqualTo("/auth/me");
    }

    @Test
    void malformedBearerTokenOnProtectedRouteReturns401ErrorResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/auth/me"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(response.hasNonNull("status")).isTrue();
        assertThat(response.hasNonNull("error")).isTrue();
        assertThat(response.hasNonNull("message")).isTrue();
        assertThat(response.hasNonNull("path")).isTrue();
    }

    @Test
    void loginDoesNotRevealWhetherUsernameExists() throws Exception {
        String username = unique("enumeration");
        createUser(username, "password123");

        MvcResult nonexistentResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", unique("missing"),
                                "password", "password123"
                        ))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPasswordResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode nonexistent = objectMapper.readTree(nonexistentResult.getResponse().getContentAsString());
        JsonNode wrongPassword = objectMapper.readTree(wrongPasswordResult.getResponse().getContentAsString());

        assertThat(nonexistent.get("status").asInt()).isEqualTo(wrongPassword.get("status").asInt());
        assertThat(nonexistent.get("error").asText()).isEqualTo(wrongPassword.get("error").asText());
        assertThat(nonexistent.get("message").asText()).isEqualTo(wrongPassword.get("message").asText());
        assertThat(nonexistent.get("path").asText()).isEqualTo(wrongPassword.get("path").asText());
    }

    @Test
    void happyPathAuthLifecycleInvalidatesTokenOnLogout() throws Exception {
        String username = unique("lifecycle");

        MvcResult createResult = createUser(username, "password123");
        JsonNode createdUser = objectMapper.readTree(createResult.getResponse().getContentAsString());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = login.get("accessToken").asText();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdUser.get("id").asLong()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult createUser(String username, String password) throws Exception {
        return mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "fullName", "Test User",
                                "password", password,
                                "role", "DEVELOPER"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String unique(String prefix) {
        return prefix + "_" + System.nanoTime();
    }
}
