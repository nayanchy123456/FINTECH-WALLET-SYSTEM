package com.paymentprocessing.wallet.transaction.service;

import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {
    TransactionResponse transfer(Long senderUserId, TransferRequest request);
    TransactionResponse deposit(Long userId, java.math.BigDecimal amount);
    TransactionResponse getTransactionByReferenceId(String referenceId);
    Page<TransactionResponse> getTransactionHistory(Long walletId, Pageable pageable);
}