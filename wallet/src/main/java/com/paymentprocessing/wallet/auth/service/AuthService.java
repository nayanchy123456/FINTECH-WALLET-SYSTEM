package com.paymentprocessing.wallet.auth.service;

import com.paymentprocessing.wallet.auth.dto.AuthResponse;
import com.paymentprocessing.wallet.auth.dto.LoginRequest;
import com.paymentprocessing.wallet.auth.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void logout(String token);
}