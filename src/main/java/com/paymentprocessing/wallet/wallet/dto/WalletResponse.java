package com.paymentprocessing.wallet.wallet.dto;

import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private BigDecimal balance;
    private WalletStatus status;
    private LocalDateTime createdAt;
}