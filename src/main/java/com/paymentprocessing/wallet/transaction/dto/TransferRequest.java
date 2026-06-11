package com.paymentprocessing.wallet.transaction.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotNull(message = "Receiver wallet ID is required")
    @Positive(message = "Receiver wallet ID must be positive")
    private Long receiverWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", inclusive = true, message = "Amount cannot exceed 1,000,000 per transfer")
    @Digits(integer = 15, fraction = 4, message = "Amount format is invalid")
    private BigDecimal amount;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @Size(max = 64, message = "Idempotency key cannot exceed 64 characters")
    private String idempotencyKey;
}