package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("User Integration Tests")
class UserIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long userId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "User Test",
                                "email", "user_test_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        token  = data.path("token").asText();
        userId = data.path("userId").asLong();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/users/profile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/profile returns 200 with user data")
    void getProfile_ShouldReturn_200() throws Exception {
        mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").isNotEmpty())
                .andExpect(jsonPath("$.data.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("GET /api/users/profile returns 401 without token")
    void getProfile_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users/profile returns correct email for authenticated user")
    void getProfile_ShouldReturn_CorrectUserData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        org.assertj.core.api.Assertions.assertThat(data.path("id").asLong()).isEqualTo(userId);
        org.assertj.core.api.Assertions.assertThat(data.path("fullName").asText()).isEqualTo("User Test");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/users/profile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/profile returns 200 and updates full name")
    void updateProfile_ShouldReturn_200_AndUpdateName() throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Updated Name"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"));
    }

    @Test
    @DisplayName("PATCH /api/users/profile persists updated name — GET returns new name")
    void updateProfile_ShouldPersist_UpdatedName() throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Persisted Name"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Persisted Name"));
    }

    @Test
    @DisplayName("PATCH /api/users/profile with blank name returns 400")
    void updateProfile_WithBlankName_ShouldReturn_400() throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/users/profile returns 401 without token")
    void updateProfile_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Some Name"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/users/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{id} returns 200 with user data")
    void getUserById_ShouldReturn_200() throws Exception {
        mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.email").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/users/{id} with non-existent ID returns 404")
    void getUserById_WithInvalidId_ShouldReturn_404() throws Exception {
        mockMvc.perform(get("/api/users/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/users/{id} returns 401 without token")
    void getUserById_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isUnauthorized());
    }
}