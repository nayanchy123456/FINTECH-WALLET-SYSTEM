package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Rate Limiter Integration Tests")
class RateLimiterIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String senderToken;
    private Long senderUserId;
    private Long receiverWalletId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Register sender
        MvcResult senderResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Sender",
                                "email", "rate_sender_" + ts + "@test.com",
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
                                "email", "rate_receiver_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String receiverToken = objectMapper.readTree(receiverResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Get sender wallet — also extracts senderUserId for Redis key cleanup
        MvcResult senderWalletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andReturn();

        senderUserId = objectMapper.readTree(senderWalletResult.getResponse().getContentAsString())
                .path("data").path("userId").asLong();

        // Get receiver wallet ID
        MvcResult walletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();

        receiverWalletId = objectMapper.readTree(walletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Clear any leftover rate limit keys for this sender before the test
        Set<String> keys = redisTemplate.keys("rate:limit:transfer:" + senderUserId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Give sender enough balance for all transfers
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "5000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("First 5 transfers succeed — 6th is blocked by rate limiter")
    void rateLimiter_ShouldBlock_After5TransfersPerMinute() throws Exception {

        // First 5 transfers — all should succeed
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/transactions/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + senderToken)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "receiverWalletId", receiverWalletId,
                                    "amount", "10.00",
                                    "description", "Transfer " + i
                            ))))
                    .andExpect(status().isOk());
        }

        // 6th transfer — should be blocked
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "10.00",
                                "description", "Should be blocked"
                        ))))
                .andExpect(status().isBadRequest());
    }
}