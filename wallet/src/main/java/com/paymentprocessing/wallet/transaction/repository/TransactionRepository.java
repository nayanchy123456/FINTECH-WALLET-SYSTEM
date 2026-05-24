package com.paymentprocessing.wallet.transaction.repository;

import com.paymentprocessing.wallet.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReferenceId(String referenceId);
    boolean existsByReferenceId(String referenceId);

    @Query("SELECT t FROM Transaction t WHERE " +
           "t.senderWallet.id = :walletId OR " +
           "t.receiverWallet.id = :walletId " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findByWalletId(@Param("walletId") Long walletId, Pageable pageable);
}