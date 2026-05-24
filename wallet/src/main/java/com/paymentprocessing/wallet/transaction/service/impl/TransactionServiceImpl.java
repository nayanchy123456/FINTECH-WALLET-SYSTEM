package com.paymentprocessing.wallet.transaction.service.impl;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.ledger.entity.AccountType;
import com.paymentprocessing.wallet.ledger.service.LedgerService;
import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import com.paymentprocessing.wallet.transaction.entity.Transaction;
import com.paymentprocessing.wallet.transaction.entity.TransactionStatus;
import com.paymentprocessing.wallet.transaction.entity.TransactionType;
import com.paymentprocessing.wallet.transaction.repository.TransactionRepository;
import com.paymentprocessing.wallet.transaction.service.TransactionService;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public TransactionResponse transfer(Long senderUserId, TransferRequest request) {
        Wallet senderWallet = walletRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));

        Wallet receiverWallet = walletRepository.findById(request.getReceiverWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found"));

        // Validations
        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new BadRequestException("Cannot transfer to same wallet");
        }
        if (senderWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Sender wallet is not active");
        }
        if (receiverWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Receiver wallet is not active");
        }
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        String referenceId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .senderWallet(senderWallet)
                .receiverWallet(receiverWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .referenceId(referenceId)
                .description(request.getDescription())
                .build();

        transactionRepository.save(transaction);

        try {
            // Update wallet balances
            senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
            receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));
            walletRepository.save(senderWallet);
            walletRepository.save(receiverWallet);

            // Get or create ledger accounts for both users
            String senderAccountCode = "USR-" + senderWallet.getId();
            String receiverAccountCode = "USR-" + receiverWallet.getId();

            ledgerService.getOrCreateAccount(
                    senderAccountCode,
                    "User Wallet Account - " + senderWallet.getId(),
                    AccountType.ASSET);

            ledgerService.getOrCreateAccount(
                    receiverAccountCode,
                    "User Wallet Account - " + receiverWallet.getId(),
                    AccountType.ASSET);

            // Record double entry
            ledgerService.recordDoubleEntry(
                    referenceId,
                    "Transfer from wallet " + senderWallet.getId() +
                    " to wallet " + receiverWallet.getId(),
                    senderAccountCode,
                    receiverAccountCode,
                    request.getAmount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            log.info("Transfer successful: {}", referenceId);

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            log.error("Transfer failed: {}", e.getMessage());
            throw new BadRequestException("Transfer failed: " + e.getMessage());
        }

        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is not active");
        }

        String referenceId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .receiverWallet(wallet)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .referenceId(referenceId)
                .description("Deposit to wallet")
                .build();

        transactionRepository.save(transaction);

        try {
            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);

            String userAccountCode = "USR-" + wallet.getId();
            ledgerService.getOrCreateAccount(
                    userAccountCode,
                    "User Wallet Account - " + wallet.getId(),
                    AccountType.ASSET);

            ledgerService.recordDoubleEntry(
                    referenceId,
                    "Deposit to wallet " + wallet.getId(),
                    "SYS-LIABILITY",
                    userAccountCode,
                    amount);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            log.info("Deposit successful: {}", referenceId);

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            log.error("Deposit failed: {}", e.getMessage());
            throw new BadRequestException("Deposit failed: " + e.getMessage());
        }

        return mapToResponse(transaction);
    }

    @Override
    public TransactionResponse getTransactionByReferenceId(String referenceId) {
        Transaction transaction = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return mapToResponse(transaction);
    }

    @Override
    public Page<TransactionResponse> getTransactionHistory(Long walletId, Pageable pageable) {
        return transactionRepository.findByWalletId(walletId, pageable)
                .map(this::mapToResponse);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .senderWalletId(transaction.getSenderWallet() != null ?
                        transaction.getSenderWallet().getId() : null)
                .receiverWalletId(transaction.getReceiverWallet() != null ?
                        transaction.getReceiverWallet().getId() : null)
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .referenceId(transaction.getReferenceId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}