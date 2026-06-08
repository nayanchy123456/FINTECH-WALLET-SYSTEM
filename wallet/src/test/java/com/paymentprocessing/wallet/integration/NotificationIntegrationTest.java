package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.notification.entity.NotificationStatus;
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

@DisplayName("Notification API Integration Tests")
class NotificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    private String userToken;
    private Long userId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Register a fresh user for each test
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Notif User",
                                "email", "notif_user_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        userToken = data.path("token").asText();
        userId    = data.path("userId").asLong();  // requires userId in AuthResponse

        // Deposit to trigger a Kafka notification event
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Wait up to 15 s for the Kafka consumer to persist the notification
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                                .isNotEmpty()
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/notifications
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/notifications returns 200 with a list for authenticated user")
    void getNotifications_ShouldReturn_200_WithList() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("GET /api/notifications returns 401 without token")
    void getNotifications_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Notifications belong only to the authenticated user")
    void getNotifications_ShouldReturn_OnlyOwnNotifications() throws Exception {
        MvcResult response = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        var dataArray = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data");

        dataArray.forEach(n ->
                assertThat(n.path("userId").asLong()).isEqualTo(userId)
        );
    }

    @Test
    @DisplayName("Deposit notification has correct type DEPOSIT")
    void depositNotification_ShouldHave_CorrectType() throws Exception {
        MvcResult response = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        var dataArray = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data");

        boolean hasDeposit = false;
        for (var n : dataArray) {
            if ("DEPOSIT".equals(n.path("type").asText())) {
                hasDeposit = true;
                break;
            }
        }
        assertThat(hasDeposit).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GET /api/notifications/unread
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/notifications/unread returns 200")
    void getUnreadNotifications_ShouldReturn_200() throws Exception {
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/notifications/unread returns 401 without token")
    void getUnreadNotifications_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PATCH /api/notifications/{id}/read
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /{id}/read marks a notification as READ and returns 200")
    void markAsRead_ShouldReturn_200_AndStatusRead() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        Long notifId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));
    }

    @Test
    @DisplayName("PATCH /{id}/read persists READ status in the database")
    void markAsRead_ShouldPersist_ReadStatus_InDB() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        Long notifId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        var notification = notificationRepository.findById(notifId);
        assertThat(notification).isPresent();
        assertThat(notification.get().getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("PATCH /{id}/read with non-existent ID returns 404")
    void markAsRead_WithInvalidId_ShouldReturn_404() throws Exception {
        mockMvc.perform(patch("/api/notifications/999999/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /{id}/read on another user's notification returns 400")
    void markAsRead_OtherUsersNotification_ShouldReturn_400() throws Exception {
        long ts = System.currentTimeMillis();

        // Register another user and trigger their notification
        MvcResult otherResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Other User",
                                "email", "other_notif_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var otherData    = objectMapper.readTree(otherResult.getResponse().getContentAsString()).path("data");
        String otherToken  = otherData.path("token").asText();
        Long   otherUserId = otherData.path("userId").asLong();

        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        // Wait for the other user's notification to be persisted
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                                .isNotEmpty()
                );

        Long otherNotifId = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(otherUserId)
                .get(0).getId();

        // Try to mark the other user's notification as read using userToken — must be 400
        mockMvc.perform(patch("/api/notifications/" + otherNotifId + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PATCH /api/notifications/read-all
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /read-all returns 200")
    void markAllAsRead_ShouldReturn_200() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /read-all with no token returns 401")
    void markAllAsRead_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /read-all marks only unread notifications as READ in DB")
    void markAllAsRead_ShouldMarkAllPending_AsRead_InDB() throws Exception {
        // Confirm there are notifications for this user
        var before = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(before).isNotEmpty();

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // After mark-all, no notification for this user should still be unread (PENDING or SENT)
        var stillUnread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> n.getStatus() != NotificationStatus.READ)
                .toList();
        assertThat(stillUnread).isEmpty();
    }

    @Test
    @DisplayName("PATCH /read-all does not affect other users' notifications")
    void markAllAsRead_ShouldNot_Affect_OtherUsers() throws Exception {
        long ts = System.currentTimeMillis();

        // Create another user with a notification
        MvcResult otherResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Another User",
                                "email", "another_notif_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var otherData    = objectMapper.readTree(otherResult.getResponse().getContentAsString()).path("data");
        String otherToken  = otherData.path("token").asText();
        Long   otherUserId = otherData.path("userId").asLong();

        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                                .isNotEmpty()
                );

        long otherUnreadBefore = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(otherUserId)
                .stream()
                .filter(n -> n.getStatus() != NotificationStatus.READ)
                .count();

        // Mark the FIRST user's notifications as read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Other user's unread count must be unchanged
        long otherUnreadAfter = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(otherUserId)
                .stream()
                .filter(n -> n.getStatus() != NotificationStatus.READ)
                .count();

        assertThat(otherUnreadAfter).isEqualTo(otherUnreadBefore);
    }
}