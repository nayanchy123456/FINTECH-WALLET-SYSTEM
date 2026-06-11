package com.paymentprocessing.wallet.auth.service;

public interface TokenBlacklistService {

    /**
     * Blacklist a token until it would naturally expire.
     * The TTL is derived from the token's own expiry claim so Redis
     * automatically evicts the entry once the token is no longer valid anyway.
     *
     * @param token raw JWT string
     */
    void blacklist(String token);

    /**
     * Returns true if the token has been explicitly blacklisted.
     */
    boolean isBlacklisted(String token);
}
