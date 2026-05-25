package com.paymentprocessing.wallet.notification.kafka;

import com.paymentprocessing.wallet.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = KafkaTopicConfig.TRANSACTION_TOPIC,
            groupId = "wallet-group"
    )
    public void consumeTransactionEvent(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received transaction event: {} from partition: {} offset: {}",
                event.getReferenceId(), partition, offset);

        try {
            notificationService.processTransactionEvent(event);
            log.info("Transaction event processed successfully: {}",
                    event.getReferenceId());
        } catch (Exception e) {
            log.error("Failed to process transaction event: {}",
                    event.getReferenceId(), e);
        }
    }
}