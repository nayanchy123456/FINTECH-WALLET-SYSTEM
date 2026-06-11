package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Idempotency Integration Tests")
class IdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private String senderToken;
    private Long receiverWalletId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Register sender
        MvcResult senderResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Sender",
                                "email", "idem_sender_" + ts + "@test.com",
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
                                "email", "idem_receiver_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String receiverToken = objectMapper.readTree(receiverResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Get receiver wallet ID
        MvcResult walletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();

        receiverWalletId = objectMapper.readTree(walletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Give sender 1000
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Same idempotency key twice returns same transaction — balance deducted only once")
    void transfer_WithSameIdempotencyKey_ShouldDeductOnlyOnce() throws Exception {
        String idempotencyKey = "idem-key-" + System.currentTimeMillis();

        Map<String, Object> payload = Map.of(
                "receiverWalletId", receiverWalletId,
                "amount", "200.00",
                "description", "Idempotent transfer",
                "idempotencyKey", idempotencyKey
        );

        // First request
        MvcResult first = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        // Second request — same key
        MvcResult second = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        // Both must return the same referenceId
        String firstRef = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        String secondRef = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        assertThat(firstRef).isEqualTo(secondRef);

        // Balance should be 800 — deducted only once
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(800.0));
    }

    @Test
    @DisplayName("Different idempotency keys create separate transactions")
    void transfer_WithDifferentKeys_ShouldDeductTwice() throws Exception {
        Map<String, Object> firstPayload = Map.of(
                "receiverWalletId", receiverWalletId,
                "amount", "100.00",
                "description", "First transfer",
                "idempotencyKey", "key-one-" + System.currentTimeMillis()
        );

        Map<String, Object> secondPayload = Map.of(
                "receiverWalletId", receiverWalletId,
                "amount", "100.00",
                "description", "Second transfer",
                "idempotencyKey", "key-two-" + System.currentTimeMillis()
        );

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(firstPayload)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(secondPayload)))
                .andExpect(status().isOk());

        // Balance should be 800 — deducted twice (100 + 100)
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(800.0));
    }
}