package com.paymentprocessing.wallet.ledger.repository;

import com.paymentprocessing.wallet.ledger.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCode(String code);
    boolean existsByCode(String code);
}