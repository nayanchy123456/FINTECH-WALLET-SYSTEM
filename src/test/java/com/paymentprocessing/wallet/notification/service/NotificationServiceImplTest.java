package com.paymentprocessing.wallet.notification.service;

import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.entity.Notification;
import com.paymentprocessing.wallet.notification.entity.NotificationStatus;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import com.paymentprocessing.wallet.notification.repository.NotificationRepository;
import com.paymentprocessing.wallet.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private TransactionEvent transferEvent;
    private TransactionEvent depositEvent;
    private Notification notification;

    @BeforeEach
    void setUp() {
        transferEvent = TransactionEvent.builder()
                .referenceId("ref-123")
                .senderUserId(1L)
                .receiverUserId(2L)
                .amount(BigDecimal.valueOf(200))
                .type("TRANSFER")
                .status("SUCCESS")
                .build();

        depositEvent = TransactionEvent.builder()
                .referenceId("ref-456")
                .receiverUserId(1L)
                .amount(BigDecimal.valueOf(500))
                .type("DEPOSIT")
                .status("SUCCESS")
                .build();

        notification = Notification.builder()
                .userId(1L)
                .title("Transfer Sent")
                .message("You sent Rs.200 successfully. Reference: ref-123")
                .referenceId("ref-123")
                .status(NotificationStatus.PENDING)
                .type("TRANSFER_SENT")
                .build();
        notification.setId(1L);
    }

    // =====================
    // PROCESS EVENT TESTS
    // =====================

    @Test
    void processTransactionEvent_ShouldCreateTwoNotifications_WhenTransferEvent() {
        notificationService.processTransactionEvent(transferEvent);

        // Transfer creates 2 notifications — one for sender, one for receiver
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void processTransactionEvent_ShouldCreateSenderNotification_WhenTransferEvent() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.processTransactionEvent(transferEvent);

        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();

        // First notification is for sender
        Notification senderNotification = saved.get(0);
        assertThat(senderNotification.getUserId()).isEqualTo(1L);
        assertThat(senderNotification.getTitle()).isEqualTo("Transfer Sent");
        assertThat(senderNotification.getType()).isEqualTo("TRANSFER_SENT");
        assertThat(senderNotification.getReferenceId()).isEqualTo("ref-123");
    }

    @Test
    void processTransactionEvent_ShouldCreateReceiverNotification_WhenTransferEvent() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.processTransactionEvent(transferEvent);

        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();

        // Second notification is for receiver
        Notification receiverNotification = saved.get(1);
        assertThat(receiverNotification.getUserId()).isEqualTo(2L);
        assertThat(receiverNotification.getTitle()).isEqualTo("Transfer Received");
        assertThat(receiverNotification.getType()).isEqualTo("TRANSFER_RECEIVED");
    }

    @Test
    void processTransactionEvent_ShouldCreateOneNotification_WhenDepositEvent() {
        notificationService.processTransactionEvent(depositEvent);

        // Deposit creates only 1 notification — for receiver
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void processTransactionEvent_ShouldCreateDepositNotification_WithCorrectDetails() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.processTransactionEvent(depositEvent);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("Deposit Successful");
        assertThat(saved.getType()).isEqualTo("DEPOSIT");
        assertThat(saved.getReferenceId()).isEqualTo("ref-456");
    }

    // =====================
    // GET NOTIFICATIONS TESTS
    // =====================

    @Test
    void getUserNotifications_ShouldReturnList_WhenUserHasNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getUserNotifications(1L);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("Transfer Sent");
    }

    @Test
    void getUserNotifications_ShouldReturnEmptyList_WhenNoNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(99L))
                .thenReturn(List.of());

        List<NotificationResponse> result = notificationService.getUserNotifications(99L);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void getUnreadNotifications_ShouldReturnOnlyUnread_WhenExists() {
        when(notificationRepository.findByUserIdAndStatus(1L, NotificationStatus.PENDING))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getUnreadNotifications(1L);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    void getUnreadNotifications_ShouldReturnEmptyList_WhenNoUnread() {
        when(notificationRepository.findByUserIdAndStatus(1L, NotificationStatus.PENDING))
                .thenReturn(List.of());

        List<NotificationResponse> result = notificationService.getUnreadNotifications(1L);

        assertThat(result).isEmpty();
    }
}