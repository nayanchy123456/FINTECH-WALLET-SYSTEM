package com.paymentprocessing.wallet.notification.kafka;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private String referenceId;
    private Long senderUserId;
    private Long receiverUserId;
    private BigDecimal amount;
    private String type;
    private String status;
}