package com.paymentprocessing.wallet.notification.service.impl;

import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.entity.Notification;
import com.paymentprocessing.wallet.notification.entity.NotificationStatus;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import com.paymentprocessing.wallet.notification.repository.NotificationRepository;
import com.paymentprocessing.wallet.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository
                .findByUserIdAndStatus(userId, NotificationStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void createNotification(Long userId, String title,
                                    String message, String referenceId,
                                    String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .status(NotificationStatus.SENT)
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
}