package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Kafka Notification Integration Tests")
class KafkaNotificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

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
                                "email", "kafka_sender_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var senderData = objectMapper.readTree(
                senderResult.getResponse().getContentAsString()).path("data");
        senderToken = senderData.path("token").asText();
        senderUserId = senderData.path("userId").asLong();

        // Register receiver
        MvcResult receiverResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Receiver",
                                "email", "kafka_receiver_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String receiverToken = objectMapper.readTree(
                receiverResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Get receiver wallet ID
        MvcResult walletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();

        receiverWalletId = objectMapper.readTree(
                walletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Give sender money
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Transfer publishes Kafka event — notification saved in DB")
    void transfer_ShouldCreateNotification_InDB() throws Exception {
        long countBefore = notificationRepository.count();

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "100.00",
                                "description", "Kafka test"
                        ))))
                .andExpect(status().isOk());

        // Wait up to 10 seconds for Kafka consumer to process
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(notificationRepository.count())
                                .isGreaterThan(countBefore)
                );
    }

    @Test
    @DisplayName("Deposit publishes Kafka event — notification saved in DB")
    void deposit_ShouldCreateNotification_InDB() throws Exception {
        long countBefore = notificationRepository.count();

        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "200.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(notificationRepository.count())
                                .isGreaterThan(countBefore)
                );
    }

    @Test
    @DisplayName("Notifications are fetchable via GET /api/notifications")
    void notifications_ShouldBeFetchable_AfterTransfer() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "50.00",
                                "description", "Notification fetch test"
                        ))))
                .andExpect(status().isOk());

        // Wait for Kafka consumer
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        mockMvc.perform(get("/api/notifications")
                                        .header("Authorization", "Bearer " + senderToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(
                                        org.hamcrest.Matchers.greaterThan(0)))
                );
    }
}