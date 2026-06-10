package com.paymentprocessing.wallet.transaction.service;

import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

public interface TransactionService {
    TransactionResponse transfer(Long senderUserId, TransferRequest request);
    TransactionResponse deposit(Long userId, BigDecimal amount);
    TransactionResponse withdraw(Long userId, BigDecimal amount);
    TransactionResponse getTransactionByReferenceId(String referenceId, Long userId);
    // Takes userId — the service is responsible for resolving the wallet internally.
    // Controllers must never reach into WalletRepository directly.
    Page<TransactionResponse> getTransactionHistory(Long userId, Pageable pageable);
}