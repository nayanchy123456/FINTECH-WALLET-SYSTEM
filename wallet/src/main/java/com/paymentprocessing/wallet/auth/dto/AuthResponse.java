package com.paymentprocessing.wallet.auth.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long userId;
    private String token;
    private String email;
    private String fullName;
    private String role;
}