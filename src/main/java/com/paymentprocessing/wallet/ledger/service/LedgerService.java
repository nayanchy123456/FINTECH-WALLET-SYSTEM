package com.paymentprocessing.wallet.ledger.service;

import com.paymentprocessing.wallet.ledger.dto.LedgerEntryResponse;
import com.paymentprocessing.wallet.ledger.entity.Account;
import java.math.BigDecimal;
import java.util.List;

public interface LedgerService {
    void recordDoubleEntry(String referenceId, String description,
                          String debitAccountCode, String creditAccountCode,
                          BigDecimal amount);
    Account getOrCreateAccount(String code, String name,
                               com.paymentprocessing.wallet.ledger.entity.AccountType type);
    List<LedgerEntryResponse> getLedgerEntriesByReference(String referenceId);
    List<LedgerEntryResponse> getLedgerEntriesByAccount(Long accountId);
}