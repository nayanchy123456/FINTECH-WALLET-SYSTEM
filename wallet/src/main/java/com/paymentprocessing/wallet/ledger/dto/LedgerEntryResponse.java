package com.paymentprocessing.wallet.ledger.dto;

import com.paymentprocessing.wallet.ledger.entity.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private TransactionType type;
    private BigDecimal amount;
    private String referenceId;
    private String description;
    private LocalDateTime createdAt;
}