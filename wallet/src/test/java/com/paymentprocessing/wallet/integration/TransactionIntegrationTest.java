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

@DisplayName("Transaction & Wallet Integration Tests")
class TransactionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long walletId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Register user
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Txn User",
                                "email", "txn_user_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        token = objectMapper.readTree(reg.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Deposit to fund the wallet and get walletId
        MvcResult dep = mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        walletId = objectMapper.readTree(dep.getResponse().getContentAsString())
                .path("data").path("receiverWalletId").asLong();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/transactions/withdraw
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /withdraw returns 200 and correct amount")
    void withdraw_ShouldReturn_200() throws Exception {
        mockMvc.perform(post("/api/transactions/withdraw")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.referenceId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /withdraw reduces wallet balance")
    void withdraw_ShouldReduce_WalletBalance() throws Exception {
        // Get balance before
        MvcResult before = mockMvc.perform(get("/api/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        double balanceBefore = objectMapper.readTree(before.getResponse().getContentAsString())
                .path("data").path("balance").asDouble();

        // Withdraw
        mockMvc.perform(post("/api/transactions/withdraw")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Get balance after
        MvcResult after = mockMvc.perform(get("/api/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        double balanceAfter = objectMapper.readTree(after.getResponse().getContentAsString())
                .path("data").path("balance").asDouble();

        assertThat(balanceAfter).isEqualTo(balanceBefore - 100.00);
    }

    @Test
    @DisplayName("POST /withdraw with insufficient balance returns 400")
    void withdraw_WithInsufficientBalance_ShouldReturn_400() throws Exception {
        mockMvc.perform(post("/api/transactions/withdraw")
                        .param("amount", "99999.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /withdraw returns 401 without token")
    void withdraw_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(post("/api/transactions/withdraw")
                        .param("amount", "100.00"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/transactions/{referenceId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /transactions/{referenceId} returns 200 with correct transaction")
    void getByReferenceId_ShouldReturn_200() throws Exception {
        // Do a deposit and capture referenceId
        MvcResult dep = mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "50.00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(dep.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        mockMvc.perform(get("/api/transactions/" + referenceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceId").value(referenceId))
                .andExpect(jsonPath("$.data.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.amount").value(50.00));
    }

    @Test
    @DisplayName("GET /transactions/{referenceId} with invalid ID returns 404")
    void getByReferenceId_WithInvalidId_ShouldReturn_404() throws Exception {
        mockMvc.perform(get("/api/transactions/non-existent-ref-id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /transactions/{referenceId} returns 401 without token")
    void getByReferenceId_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/transactions/some-ref-id"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/transactions/history
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /transactions/history returns 200 with paginated list")
    void getHistory_ShouldReturn_200() throws Exception {
        mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("GET /transactions/history returns 401 without token")
    void getHistory_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/transactions/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /transactions/history respects page and size params")
    void getHistory_ShouldRespect_Pagination() throws Exception {
        mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/wallets/{walletId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /wallets/{walletId} returns 200 with wallet data")
    void getWalletById_ShouldReturn_200() throws Exception {
        mockMvc.perform(get("/api/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(walletId))
                .andExpect(jsonPath("$.data.balance").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /wallets/{walletId} with non-existent ID returns 404")
    void getWalletById_WithInvalidId_ShouldReturn_404() throws Exception {
        mockMvc.perform(get("/api/wallets/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /wallets/{walletId} returns 401 without token")
    void getWalletById_WithoutToken_ShouldReturn_401() throws Exception {
        mockMvc.perform(get("/api/wallets/" + walletId))
                .andExpect(status().isUnauthorized());
    }
}