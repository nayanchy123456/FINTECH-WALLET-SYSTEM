package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

        // User must exist in DB
        var user = userRepository.findByEmail(email);
        assertThat(user).isPresent();

        // Wallet must be auto-created
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

        // First registration — success
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Second registration — should fail
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

        // Register first
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Login User",
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk());

        // Now login
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

        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "User",
                                "email", email,
                                "password", "CorrectPassword123!"
                        ))))
                .andExpect(status().isOk());

        // Login with wrong password
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "WrongPassword!"
                        ))))
                .andExpect(status().is4xxClientError());
    }
}