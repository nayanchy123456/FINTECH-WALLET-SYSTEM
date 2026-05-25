package com.paymentprocessing.wallet.notification.repository;

import com.paymentprocessing.wallet.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndStatus(Long userId,
            com.paymentprocessing.wallet.notification.entity.NotificationStatus status);
}