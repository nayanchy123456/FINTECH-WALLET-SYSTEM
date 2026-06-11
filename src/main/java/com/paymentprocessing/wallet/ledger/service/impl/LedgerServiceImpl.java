package com.paymentprocessing.wallet.ledger.service.impl;

import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.ledger.dto.LedgerEntryResponse;
import com.paymentprocessing.wallet.ledger.entity.*;
import com.paymentprocessing.wallet.ledger.repository.AccountRepository;
import com.paymentprocessing.wallet.ledger.repository.LedgerEntryRepository;
import com.paymentprocessing.wallet.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerServiceImpl implements LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void recordDoubleEntry(String referenceId, String description,
                                  String debitAccountCode, String creditAccountCode,
                                  BigDecimal amount) {

        Account debitAccount = accountRepository.findByCode(debitAccountCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + debitAccountCode));

        Account creditAccount = accountRepository.findByCode(creditAccountCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + creditAccountCode));

        // Debit entry
        LedgerEntry debitEntry = LedgerEntry.builder()
                .account(debitAccount)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .referenceId(referenceId)
                .description(description)
                .build();

        // Credit entry
        LedgerEntry creditEntry = LedgerEntry.builder()
                .account(creditAccount)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .referenceId(referenceId)
                .description(description)
                .build();

        // Update account balances
        debitAccount.setBalance(debitAccount.getBalance().subtract(amount));
        creditAccount.setBalance(creditAccount.getBalance().add(amount));

        accountRepository.save(debitAccount);
        accountRepository.save(creditAccount);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Account getOrCreateAccount(String code, String name, AccountType type) {
        return accountRepository.findByCode(code)
                .orElseGet(() -> accountRepository.save(
                        Account.builder()
                                .code(code)
                                .name(name)
                                .type(type)
                                .build()
                ));
    }

    @Override
    public List<LedgerEntryResponse> getLedgerEntriesByReference(String referenceId) {
        return ledgerEntryRepository.findByReferenceId(referenceId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntryResponse> getLedgerEntriesByAccount(Long accountId) {
        return ledgerEntryRepository.findByAccountId(accountId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private LedgerEntryResponse mapToResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .accountId(entry.getAccount().getId())
                .accountCode(entry.getAccount().getCode())
                .type(entry.getType())
                .amount(entry.getAmount())
                .referenceId(entry.getReferenceId())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}