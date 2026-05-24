package com.paymentprocessing.wallet.wallet.controller;

import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import com.paymentprocessing.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet() {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        WalletResponse response = walletService.createWallet(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Wallet created successfully"));
    }

    @GetMapping("/my-wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet() {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        WalletResponse response = walletService.getWalletByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Wallet fetched successfully"));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletById(
            @PathVariable Long walletId) {
        WalletResponse response = walletService.getWalletById(walletId);
        return ResponseEntity.ok(ApiResponse.success(response, "Wallet fetched successfully"));
    }
}