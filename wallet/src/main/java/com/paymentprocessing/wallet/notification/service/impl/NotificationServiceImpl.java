package com.paymentprocessing.wallet.notification.service.impl;

import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.entity.Notification;
import com.paymentprocessing.wallet.notification.entity.NotificationStatus;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import com.paymentprocessing.wallet.notification.repository.NotificationRepository;
import com.paymentprocessing.wallet.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void processTransactionEvent(TransactionEvent event) {
        if (event.getType().equals("TRANSFER")) {
            // Notification for sender
            createNotification(
                    event.getSenderUserId(),
                    "Transfer Sent",
                    "You sent Rs." + event.getAmount() +
                    " successfully. Reference: " + event.getReferenceId(),
                    event.getReferenceId(),
                    "TRANSFER_SENT"
            );

            // Notification for receiver
            createNotification(
                    event.getReceiverUserId(),
                    "Transfer Received",
                    "You received Rs." + event.getAmount() +
                    ". Reference: " + event.getReferenceId(),
                    event.getReferenceId(),
                    "TRANSFER_RECEIVED"
            );

        } else if (event.getType().equals("DEPOSIT")) {
            createNotification(
                    event.getReceiverUserId(),
                    "Deposit Successful",
                    "Rs." + event.getAmount() +
                    " deposited to your wallet. Reference: " + event.getReferenceId(),
                    event.getReferenceId(),
                    "DEPOSIT"
            );
        } else if (event.getType().equals("WITHDRAWAL")) {
            createNotification(
                    event.getSenderUserId(),
                    "Withdrawal Successful",
                    "Rs." + event.getAmount() +
                    " withdrawn from your wallet. Reference: " + event.getReferenceId(),
                    event.getReferenceId(),
                    "WITHDRAWAL"
            );
        }
    }

    @Override
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository
                .findByUserIdAndStatus(userId, NotificationStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NotificationResponse> getUnreadNotifications(Long userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdAndStatus(userId, NotificationStatus.PENDING, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Creates a new notification with PENDING status (= unread).
     * PENDING is the canonical "unread" state used throughout the system.
     */
    private void createNotification(Long userId, String title,
                                    String message, String referenceId,
                                    String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .status(NotificationStatus.PENDING)   // was SENT — fixed to PENDING
                .type(type)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for user: {} type: {}", userId, type);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceId(notification.getReferenceId())
                .status(notification.getStatus())
                .type(notification.getType())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new BadRequestException(
                    "You are not authorized to update this notification");
        }

        notification.setStatus(NotificationStatus.READ);
        Notification updated = notificationRepository.save(notification);
        log.info("Notification marked as read: {} for user: {}", notificationId, userId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository
                .findByUserIdAndStatus(userId, NotificationStatus.PENDING);

        unread.forEach(n -> n.setStatus(NotificationStatus.READ));
        notificationRepository.saveAll(unread);
        log.info("All notifications marked as read for user: {}", userId);
    }
}