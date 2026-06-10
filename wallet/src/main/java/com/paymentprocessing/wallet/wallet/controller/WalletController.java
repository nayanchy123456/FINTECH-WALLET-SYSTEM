package com.paymentprocessing.wallet.wallet.controller;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import com.paymentprocessing.wallet.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet management APIs")
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

        // Ownership check — fetch the wallet first, then verify the
        // authenticated user is the owner. WalletResponse.userId is set
        // directly from wallet.getUser().getId() in WalletServiceImpl so
        // this is equivalent to wallet.getUser().getId() comparison.
        String email = SecurityUtil.getCurrentUserEmail();
        User currentUser = userService.findByEmail(email);
        WalletResponse response = walletService.getWalletById(walletId);

        if (!currentUser.getId().equals(response.getUserId())) {
            throw new BadRequestException(
                    "Access denied: wallet does not belong to the current user");
        }

        return ResponseEntity.ok(ApiResponse.success(response, "Wallet fetched successfully"));
    }
}