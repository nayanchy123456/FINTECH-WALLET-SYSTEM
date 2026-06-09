package com.paymentprocessing.wallet.wallet.repository;

import com.paymentprocessing.wallet.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // -----------------------------------------------------------------------
    // FIX: Pessimistic write lock for single-wallet operations (deposit /
    // withdraw).  SELECT ... FOR UPDATE prevents another transaction from
    // reading the row until this transaction commits, eliminating the TOCTOU
    // window between the balance-check and the balance-update.
    // Use these methods in deposit() and withdraw() instead of the plain
    // findByUserId / findById variants.
    // -----------------------------------------------------------------------

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}