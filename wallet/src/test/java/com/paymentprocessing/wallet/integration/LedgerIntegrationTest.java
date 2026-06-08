package com.paymentprocessing.wallet.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.ledger.entity.AccountType;
import com.paymentprocessing.wallet.ledger.repository.AccountRepository;
import com.paymentprocessing.wallet.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Ledger Integration Tests")
class LedgerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String userToken;
    private Long userWalletId;

    private String senderToken;
    private String receiverToken;
    private Long senderWalletId;
    private Long receiverWalletId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();

        // Single user for deposit/withdrawal scenarios
        MvcResult userResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ledger User",
                                "email", "ledger_user_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        userToken = objectMapper.readTree(userResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        MvcResult walletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        userWalletId = objectMapper.readTree(walletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Sender and receiver for transfer scenarios
        MvcResult senderResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ledger Sender",
                                "email", "ledger_sender_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        senderToken = objectMapper.readTree(senderResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        MvcResult senderWalletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andReturn();

        senderWalletId = objectMapper.readTree(senderWalletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult receiverResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ledger Receiver",
                                "email", "ledger_receiver_" + ts + "@test.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        receiverToken = objectMapper.readTree(receiverResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        MvcResult receiverWalletResult = mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();

        receiverWalletId = objectMapper.readTree(receiverWalletResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. System accounts bootstrapped by DataInitializer
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("System accounts SYS-LIABILITY, SYS-CASH, SYS-REVENUE are created on startup")
    void systemAccounts_ShouldExist_AfterStartup() {
        assertThat(accountRepository.findByCode("SYS-LIABILITY")).isPresent();
        assertThat(accountRepository.findByCode("SYS-CASH")).isPresent();
        assertThat(accountRepository.findByCode("SYS-REVENUE")).isPresent();
    }

    @Test
    @DisplayName("SYS-LIABILITY account type is LIABILITY")
    void sysLiabilityAccount_ShouldHave_CorrectType() {
        accountRepository.findByCode("SYS-LIABILITY").ifPresent(account ->
                assertThat(account.getType()).isEqualTo(AccountType.LIABILITY)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Deposit creates a balanced double-entry (DEBIT SYS-LIABILITY / CREDIT USR-wallet)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deposit creates exactly two ledger entries for the reference ID")
    void deposit_ShouldCreate_TwoLedgerEntries() throws Exception {
        MvcResult depositResult = mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                depositResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);
        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("Deposit ledger entries are balanced — total debits equal total credits")
    void deposit_LedgerEntries_ShouldBeBalanced() throws Exception {
        MvcResult depositResult = mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "750.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                depositResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getType().name().equals("DEBIT"))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getType().name().equals("CREDIT"))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebits).isEqualByComparingTo(totalCredits);
    }

    @Test
    @DisplayName("Deposit creates a user ledger account with code USR-{walletId}")
    void deposit_ShouldCreate_UserLedgerAccount() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "200.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String expectedCode = "USR-" + userWalletId;
        assertThat(accountRepository.findByCode(expectedCode)).isPresent();
    }

    @Test
    @DisplayName("Deposit user ledger account type is ASSET")
    void deposit_UserLedgerAccount_ShouldBeAssetType() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "300.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String expectedCode = "USR-" + userWalletId;
        accountRepository.findByCode(expectedCode).ifPresent(account ->
                assertThat(account.getType()).isEqualTo(AccountType.ASSET)
        );
    }

    @Test
    @DisplayName("Multiple deposits each produce independent balanced entry pairs")
    void multipleDeposits_EachShouldHave_OwnLedgerEntries() throws Exception {
        long entriesBefore = ledgerEntryRepository.count();

        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "200.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        long entriesAfter = ledgerEntryRepository.count();
        // Each deposit creates 2 entries → net +4
        assertThat(entriesAfter - entriesBefore).isEqualTo(4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Transfer creates a balanced double-entry (DEBIT sender / CREDIT receiver)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Transfer creates exactly two ledger entries for the reference ID")
    void transfer_ShouldCreate_TwoLedgerEntries() throws Exception {
        // Fund sender first
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        MvcResult transferResult = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "400.00",
                                "description", "Ledger test transfer"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                transferResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);
        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("Transfer ledger entries are balanced — total debits equal total credits")
    void transfer_LedgerEntries_ShouldBeBalanced() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        MvcResult transferResult = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "250.00",
                                "description", "Balance check"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                transferResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getType().name().equals("DEBIT"))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getType().name().equals("CREDIT"))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebits).isEqualByComparingTo(totalCredits);
    }

    @Test
    @DisplayName("Transfer creates ledger accounts for both sender and receiver wallets")
    void transfer_ShouldCreate_LedgerAccountsForBothParties() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "600.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "100.00",
                                "description", "Account creation check"
                        ))))
                .andExpect(status().isOk());

        assertThat(accountRepository.findByCode("USR-" + senderWalletId)).isPresent();
        assertThat(accountRepository.findByCode("USR-" + receiverWalletId)).isPresent();
    }

    @Test
    @DisplayName("Transfer ledger entry amount matches the transferred amount exactly")
    void transfer_LedgerEntryAmount_ShouldMatch_TransferAmount() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        BigDecimal transferAmount = new BigDecimal("333.33");

        MvcResult transferResult = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", transferAmount.toPlainString(),
                                "description", "Amount precision check"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                transferResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);
        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.getAmount()).isEqualByComparingTo(transferAmount)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Ledger account balance updates
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("After deposit, user ledger account balance equals deposited amount")
    void deposit_ShouldIncrease_LedgerAccountBalance() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String code = "USR-" + userWalletId;
        var account = accountRepository.findByCode(code);
        assertThat(account).isPresent();
        assertThat(account.get().getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("After transfer, sender ledger account balance decreases and receiver increases")
    void transfer_ShouldUpdate_BothLedgerAccountBalances() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "1000.00")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "300.00",
                                "description", "Balance update check"
                        ))))
                .andExpect(status().isOk());

        var senderAccount = accountRepository.findByCode("USR-" + senderWalletId);
        var receiverAccount = accountRepository.findByCode("USR-" + receiverWalletId);

        assertThat(senderAccount).isPresent();
        assertThat(receiverAccount).isPresent();

        // Sender: deposited 1000, then debited 300 → net +700 on the asset account
        // (deposit CREDITS the user account; transfer DEBITS it)
        // The LedgerServiceImpl subtracts on debit and adds on credit
        assertThat(senderAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(receiverAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Ledger entries persist the correct reference ID and description
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ledger entries carry the same referenceId as the transaction")
    void ledgerEntries_ShouldCarry_TransactionReferenceId() throws Exception {
        MvcResult depositResult = mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = objectMapper.readTree(
                depositResult.getResponse().getContentAsString())
                .path("data").path("referenceId").asText();

        var entries = ledgerEntryRepository.findByReferenceId(referenceId);
        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.getReferenceId()).isEqualTo(referenceId)
        );
    }

    @Test
    @DisplayName("Ledger entries can be queried by user ledger account ID")
    void ledgerEntries_ShouldBeQueryable_ByAccountId() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "100.00")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        var userAccount = accountRepository.findByCode("USR-" + userWalletId);
        assertThat(userAccount).isPresent();

        var entries = ledgerEntryRepository.findByAccountId(userAccount.get().getId());
        assertThat(entries).isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Failed transaction must not leave orphaned ledger entries
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Failed transfer due to insufficient balance creates no ledger entries")
    void failedTransfer_ShouldNot_CreateLedgerEntries() throws Exception {
        // Sender has zero balance — transfer should fail
        long entriesBefore = ledgerEntryRepository.count();

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + senderToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverWalletId", receiverWalletId,
                                "amount", "9999.00",
                                "description", "Should fail"
                        ))))
                .andExpect(status().isBadRequest());

        long entriesAfter = ledgerEntryRepository.count();
        assertThat(entriesAfter).isEqualTo(entriesBefore);
    }
}