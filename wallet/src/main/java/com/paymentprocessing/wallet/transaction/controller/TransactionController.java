package com.paymentprocessing.wallet.transaction.controller;

import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import com.paymentprocessing.wallet.transaction.service.TransactionService;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "Transfer and deposit APIs")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserService userService;
    private final WalletRepository walletRepository;


    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        TransactionResponse response = transactionService.transfer(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer successful"));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @RequestParam BigDecimal amount) {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        TransactionResponse response = transactionService.deposit(user.getId(), amount);
        return ResponseEntity.ok(ApiResponse.success(response, "Deposit successful"));
    }

 @GetMapping("/history")
public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    String email = SecurityUtil.getCurrentUserEmail();
    User user = userService.findByEmail(email);
    // Get wallet first then pass walletId
    Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    Pageable pageable = PageRequest.of(page, size);
    Page<TransactionResponse> response = transactionService
            .getTransactionHistory(wallet.getId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(response,
            "Transaction history fetched successfully"));
}

    @GetMapping("/{referenceId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReferenceId(
            @PathVariable String referenceId) {
        TransactionResponse response = transactionService
                .getTransactionByReferenceId(referenceId);
        return ResponseEntity.ok(ApiResponse.success(response,
                "Transaction fetched successfully"));
    }
}