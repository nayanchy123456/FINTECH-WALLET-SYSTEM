package com.paymentprocessing.wallet.ledger.repository;

import com.paymentprocessing.wallet.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByReferenceId(String referenceId);
    List<LedgerEntry> findByAccountId(Long accountId);
}