package com.paymentprocessing.wallet.wallet.service;

import com.paymentprocessing.wallet.wallet.dto.WalletResponse;

public interface WalletService {
    WalletResponse createWallet(Long userId);
    WalletResponse getWalletByUserId(Long userId);
    WalletResponse getWalletById(Long walletId);
}