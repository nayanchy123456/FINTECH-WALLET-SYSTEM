package com.paymentprocessing.wallet.auth.service.impl;

import com.paymentprocessing.wallet.auth.service.TokenBlacklistService;
import com.paymentprocessing.wallet.auth.security.JwtService;
import com.paymentprocessing.wallet.common.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final RedisService redisService;
    private final JwtService jwtService;

    @Override
    public void blacklist(String token) {
        Duration ttl = jwtService.getRemainingTtl(token);
        if (ttl.isNegative() || ttl.isZero()) {
            // Token is already expired — no need to store it
            log.debug("Token already expired, skipping blacklist entry");
            return;
        }
        String key = BLACKLIST_PREFIX + token;
        redisService.set(key, "blacklisted", ttl);
        log.info("Token blacklisted, expires in {}s", ttl.toSeconds());
    }

    @Override
    public boolean isBlacklisted(String token) {
        return redisService.exists(BLACKLIST_PREFIX + token);
    }
}
