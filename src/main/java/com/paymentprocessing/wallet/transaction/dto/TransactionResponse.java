package com.paymentprocessing.wallet.transaction.dto;

import com.paymentprocessing.wallet.transaction.entity.TransactionStatus;
import com.paymentprocessing.wallet.transaction.entity.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private Long senderWalletId;
    private Long receiverWalletId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private String referenceId;
    private String description;
    private LocalDateTime createdAt;
}