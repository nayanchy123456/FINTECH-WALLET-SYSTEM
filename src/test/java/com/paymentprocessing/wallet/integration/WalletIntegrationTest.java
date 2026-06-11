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

@DisplayName("Wallet Integration Tests")
class WalletIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        String email = "wallet_" + System.currentTimeMillis() + "@test.com";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Wallet User",
                                "email", email,
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    @DisplayName("Wallet is auto-created on register with zero balance")
    void wallet_ShouldBeCreated_WithZeroBalance() throws Exception {
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0.0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Get wallet without token returns 403")
    void wallet_ShouldFail_WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/wallets/my-wallet"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Balance increases after deposit")
    void balance_ShouldIncrease_AfterDeposit() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(500.0));
    }

    @Test
    @DisplayName("Wallet has ACTIVE status by default")
    void wallet_ShouldHaveActiveStatus_ByDefault() throws Exception {
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }
}