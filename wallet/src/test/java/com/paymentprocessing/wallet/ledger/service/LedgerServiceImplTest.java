package com.paymentprocessing.wallet.ledger.service;

import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.ledger.dto.LedgerEntryResponse;
import com.paymentprocessing.wallet.ledger.entity.Account;
import com.paymentprocessing.wallet.ledger.entity.AccountType;
import com.paymentprocessing.wallet.ledger.entity.LedgerEntry;
import com.paymentprocessing.wallet.ledger.entity.TransactionType;
import com.paymentprocessing.wallet.ledger.repository.AccountRepository;
import com.paymentprocessing.wallet.ledger.repository.LedgerEntryRepository;
import com.paymentprocessing.wallet.ledger.service.impl.LedgerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    private Account debitAccount;
    private Account creditAccount;

    @BeforeEach
    void setUp() {
        debitAccount = Account.builder()
                .code("USR-1")
                .name("User Wallet Account - 1")
                .type(AccountType.ASSET)
                .balance(BigDecimal.valueOf(1000))
                .build();
        debitAccount.setId(1L);

        creditAccount = Account.builder()
                .code("USR-2")
                .name("User Wallet Account - 2")
                .type(AccountType.ASSET)
                .balance(BigDecimal.valueOf(500))
                .build();
        creditAccount.setId(2L);
    }

    // =====================
    // DOUBLE ENTRY TESTS
    // =====================

    @Test
    void recordDoubleEntry_ShouldCreateTwoLedgerEntries() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findByCode("USR-2")).thenReturn(Optional.of(creditAccount));

        ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "USR-1", "USR-2",
                BigDecimal.valueOf(200));

        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void recordDoubleEntry_ShouldDebitSenderAndCreditReceiver() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findByCode("USR-2")).thenReturn(Optional.of(creditAccount));

        ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "USR-1", "USR-2",
                BigDecimal.valueOf(200));

        assertThat(debitAccount.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(800));
        assertThat(creditAccount.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(700));
    }

    @Test
    void recordDoubleEntry_ShouldSaveBothAccounts() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findByCode("USR-2")).thenReturn(Optional.of(creditAccount));

        ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "USR-1", "USR-2",
                BigDecimal.valueOf(200));

        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    void recordDoubleEntry_ShouldThrowException_WhenDebitAccountNotFound() {
        when(accountRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "INVALID", "USR-2",
                BigDecimal.valueOf(200)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found: INVALID");
    }

    @Test
    void recordDoubleEntry_ShouldThrowException_WhenCreditAccountNotFound() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "USR-1", "INVALID",
                BigDecimal.valueOf(200)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found: INVALID");
    }

    @Test
    void recordDoubleEntry_ShouldBalanceEquation() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findByCode("USR-2")).thenReturn(Optional.of(creditAccount));

        BigDecimal initialTotal = debitAccount.getBalance()
                .add(creditAccount.getBalance());

        ledgerService.recordDoubleEntry(
                "ref-001", "Transfer",
                "USR-1", "USR-2",
                BigDecimal.valueOf(200));

        BigDecimal finalTotal = debitAccount.getBalance()
                .add(creditAccount.getBalance());

        assertThat(finalTotal).isEqualByComparingTo(initialTotal);
    }

    // =====================
    // GET OR CREATE ACCOUNT TESTS
    // =====================

    @Test
    void getOrCreateAccount_ShouldReturnExisting_WhenAccountExists() {
        when(accountRepository.findByCode("USR-1")).thenReturn(Optional.of(debitAccount));

        Account result = ledgerService.getOrCreateAccount(
                "USR-1", "User Wallet Account - 1", AccountType.ASSET);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("USR-1");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void getOrCreateAccount_ShouldCreateNew_WhenAccountNotExists() {
        when(accountRepository.findByCode("USR-99")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(debitAccount);

        Account result = ledgerService.getOrCreateAccount(
                "USR-99", "New Account", AccountType.ASSET);

        assertThat(result).isNotNull();
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    // =====================
    // GET LEDGER ENTRIES TESTS
    // =====================

    @Test
    void getLedgerEntriesByReference_ShouldReturnEntries_WhenExists() {
        LedgerEntry entry = LedgerEntry.builder()
                .account(debitAccount)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(200))
                .referenceId("ref-001")
                .description("Transfer")
                .build();
        entry.setId(1L);

        when(ledgerEntryRepository.findByReferenceId("ref-001"))
                .thenReturn(List.of(entry));

        List<LedgerEntryResponse> result =
                ledgerService.getLedgerEntriesByReference("ref-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReferenceId()).isEqualTo("ref-001");
        assertThat(result.get(0).getType()).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void getLedgerEntriesByReference_ShouldReturnEmpty_WhenNotExists() {
        when(ledgerEntryRepository.findByReferenceId(anyString()))
                .thenReturn(List.of());

        List<LedgerEntryResponse> result =
                ledgerService.getLedgerEntriesByReference("non-existent");

        assertThat(result).isEmpty();
    }

    @Test
    void getLedgerEntriesByAccount_ShouldReturnEntries_WhenExists() {
        LedgerEntry entry = LedgerEntry.builder()
                .account(debitAccount)
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(300))
                .referenceId("ref-002")
                .description("Deposit")
                .build();
        entry.setId(2L);

        when(ledgerEntryRepository.findByAccountId(1L))
                .thenReturn(List.of(entry));

        List<LedgerEntryResponse> result =
                ledgerService.getLedgerEntriesByAccount(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(300));
    }
}