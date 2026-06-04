package com.paymentprocessing.wallet.notification.service;

import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import java.util.List;

public interface NotificationService {
    void processTransactionEvent(TransactionEvent event);
    List<NotificationResponse> getUserNotifications(Long userId);
    List<NotificationResponse> getUnreadNotifications(Long userId);
    NotificationResponse markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}