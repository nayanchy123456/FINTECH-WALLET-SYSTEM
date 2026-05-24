package com.paymentprocessing.wallet.wallet.service.impl;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.common.service.RedisService;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import com.paymentprocessing.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserService userService;
    private final RedisService redisService;

    private static final String WALLET_CACHE_KEY = "wallet:balance:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Override
    @Transactional
    public WalletResponse createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new BadRequestException("Wallet already exists for this user");
        }
        User user = userService.findById(userId);
        Wallet wallet = Wallet.builder()
                .user(user)
                .build();
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet created for user: {}", userId);
        return mapToResponse(saved);
    }

    @Override
    public WalletResponse getWalletByUserId(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for user"));
        return mapToResponse(wallet);
    }

    @Override
    public WalletResponse getWalletById(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found"));
        return mapToResponse(wallet);
    }

    @Override
    public BigDecimal getCachedBalance(Long walletId) {
        String cacheKey = WALLET_CACHE_KEY + walletId;
        return redisService.get(cacheKey)
                .map(value -> new BigDecimal(value.toString()))
                .orElseGet(() -> {
                    log.info("Cache miss for wallet: {}", walletId);
                    Wallet wallet = walletRepository.findById(walletId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Wallet not found"));
                    redisService.set(cacheKey,
                            wallet.getBalance().toString(), CACHE_TTL);
                    return wallet.getBalance();
                });
    }

    @Override
    public void updateCachedBalance(Long walletId, BigDecimal balance) {
        String cacheKey = WALLET_CACHE_KEY + walletId;
        redisService.set(cacheKey, balance.toString(), CACHE_TTL);
        log.info("Cache updated for wallet: {}", walletId);
    }

    @Override
    public void evictBalanceCache(Long walletId) {
        String cacheKey = WALLET_CACHE_KEY + walletId;
        redisService.delete(cacheKey);
        log.info("Cache evicted for wallet: {}", walletId);
    }

    private WalletResponse mapToResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .userEmail(wallet.getUser().getEmail())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}