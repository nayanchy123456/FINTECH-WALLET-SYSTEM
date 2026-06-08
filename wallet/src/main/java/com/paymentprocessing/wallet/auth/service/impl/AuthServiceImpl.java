package com.paymentprocessing.wallet.auth.service.impl;

import com.paymentprocessing.wallet.auth.dto.*;
import com.paymentprocessing.wallet.auth.security.JwtService;
import com.paymentprocessing.wallet.auth.service.AuthService;
import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final WalletService walletService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        // Auto create wallet for new user
        walletService.createWallet(savedUser.getId());

        String token = jwtService.generateToken(savedUser.getEmail());

        return AuthResponse.builder()
                .userId(savedUser.getId())
                .token(token)
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole().name())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String token = jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .userId(user.getId())
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}