package com.paymentprocessing.wallet.notification.repository;

import com.paymentprocessing.wallet.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndStatus(Long userId,
            com.paymentprocessing.wallet.notification.entity.NotificationStatus status);
    Page<Notification> findByUserIdAndStatus(Long userId,
            com.paymentprocessing.wallet.notification.entity.NotificationStatus status,
            Pageable pageable);
}