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

@DisplayName("Transfer Integration Tests")
class TransferIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private String senderToken;
    private String receiverToken;
    private Long receiverWalletId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Register sender
        MvcResult senderResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Sender",
                                "email", "sender_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        senderToken = objectMapper.readTree(senderResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Register receiver
        MvcResult receiverResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Receiver",
                                "email", "receiver_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        receiverToken = objectMapper.readTree(receiverResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Get receiver wallet ID
        MvcResult walletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();

        receiverWalletId = objectMapper.readTree(walletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Give sender 1000 to work with
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Transfer reduces sender balance and increases receiver balance")
    void transfer_ShouldUpdateBothBalances() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "300.00",
                                "description", "Test transfer"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(300.0));

        // Sender should have 700 left
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(700.0));

        // Receiver should have 300
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(300.0));
    }

    @Test
    @DisplayName("Transfer fails when sender has insufficient balance")
    void transfer_ShouldFail_WhenInsufficientBalance() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "9999.00",
                                "description", "Should fail"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Transfer fails without auth token")
    void transfer_ShouldFail_WithoutAuth() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "100.00",
                                "description", "No auth"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Transaction history shows completed transfers")
    void transfer_ShouldAppear_InTransactionHistory() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "100.00",
                                "description", "History test"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }
}