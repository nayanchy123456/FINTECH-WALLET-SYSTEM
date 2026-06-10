package com.paymentprocessing.wallet.notification.service;

import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface NotificationService {
    void processTransactionEvent(TransactionEvent event);
    List<NotificationResponse> getUserNotifications(Long userId);
    Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable);
    List<NotificationResponse> getUnreadNotifications(Long userId);
    Page<NotificationResponse> getUnreadNotifications(Long userId, Pageable pageable);
    NotificationResponse markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}