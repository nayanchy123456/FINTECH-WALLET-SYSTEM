package com.paymentprocessing.wallet.common.service;

import java.time.Duration;
import java.util.Optional;

public interface RedisService {
    void set(String key, Object value, Duration ttl);
    Optional<Object> get(String key);
    void delete(String key);
    boolean exists(String key);
    boolean setIfAbsent(String key, Object value, Duration ttl);
}