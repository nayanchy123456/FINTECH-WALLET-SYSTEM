package com.paymentprocessing.wallet.wallet.service;

import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import java.math.BigDecimal;

public interface WalletService {
    WalletResponse createWallet(Long userId);
    WalletResponse getWalletByUserId(Long userId);
    WalletResponse getWalletById(Long walletId);
    BigDecimal getCachedBalance(Long walletId);
    void updateCachedBalance(Long walletId, BigDecimal balance);
    void evictBalanceCache(Long walletId);
}