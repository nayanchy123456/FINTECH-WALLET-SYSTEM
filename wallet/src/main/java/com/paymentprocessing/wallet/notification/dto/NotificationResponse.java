package com.paymentprocessing.wallet.notification.dto;

import com.paymentprocessing.wallet.notification.entity.NotificationStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String referenceId;
    private NotificationStatus status;
    private String type;
    private LocalDateTime createdAt;
}