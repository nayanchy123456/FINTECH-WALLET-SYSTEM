package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Auth Integration Tests")
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Test User",
                                "email", email,
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    // ── existing tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register should save user and auto-create wallet")
    void register_ShouldSaveUser_AndCreateWallet() throws Exception {
        String email = "auth_test_" + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Test User",
                                "email", email,
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email));

        var user = userRepository.findByEmail(email);
        assertThat(user).isPresent();
        assertThat(walletRepository.findByUserId(user.get().getId())).isPresent();
    }

    @Test
    @DisplayName("Register with duplicate email should fail")
    void register_ShouldFail_WhenEmailAlreadyExists() throws Exception {
        String email = "dupe_" + System.currentTimeMillis() + "@test.com";

        Map<String, String> payload = Map.of(
                "fullName", "Test User",
                "email", email,
                "password", "Password123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Password should be BCrypt hashed in DB")
    void register_ShouldHashPassword() throws Exception {
        String email = "hash_" + System.currentTimeMillis() + "@test.com";
        String rawPassword = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Hash User",
                                "email", email,
                                "password", rawPassword
                        ))))
                .andExpect(status().isOk());

        var user = userRepository.findByEmail(email).get();
        assertThat(user.getPassword()).isNotEqualTo(rawPassword);
        assertThat(user.getPassword()).startsWith("$2a$");
    }

    @Test
    @DisplayName("Login should return JWT token")
    void login_ShouldReturnToken() throws Exception {
        String email = "login_" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Login User",
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    @DisplayName("Login with wrong password should fail")
    void login_ShouldFail_WithWrongPassword() throws Exception {
        String email = "wrongpass_" + System.currentTimeMillis() + "@test.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "User",
                                "email", email,
                                "password", "CorrectPassword123!"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "WrongPassword!"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    // ── logout tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Logout should succeed with a valid token")
    void logout_ShouldReturnOk_WithValidToken() throws Exception {
        String token = registerAndGetToken("logout_ok_" + System.currentTimeMillis() + "@test.com");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("Blacklisted token should be rejected on protected endpoints")
    void logout_ShouldBlacklistToken_AndRejectSubsequentRequests() throws Exception {
        String token = registerAndGetToken("logout_reject_" + System.currentTimeMillis() + "@test.com");

        // Token works before logout
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logout — token is blacklisted
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Same token should now be rejected
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("After logout, a fresh login token should still work")
    void logout_ShouldOnlyInvalidateLoggedOutToken_NotNewTokens() throws Exception {
        String email = "logout_fresh_" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Fresh User",
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk());

        // Get first token and log it out
        String firstToken = objectMapper.readTree(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "email", email, "password", password))))
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("token").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        // Login again — new token
        String newToken = objectMapper.readTree(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "email", email, "password", password))))
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("token").asText();

        // New token should work fine
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());

        // Old token is still blacklisted
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout without Authorization header should fail with 400")
    void logout_ShouldFail_WithoutAuthHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout endpoint should be secured (no unauthenticated bypass)")
    void logout_ShouldRequireAuthentication() throws Exception {
        // Malformed / missing token — Spring should return 400 (missing header)
        // or 401 if we send a garbage token
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().is4xxClientError());
    }
}
