package com.paymentprocessing.wallet.notification.kafka;

import com.paymentprocessing.wallet.notification.entity.FailedMessage;
import com.paymentprocessing.wallet.notification.entity.FailedMessageStatus;
import com.paymentprocessing.wallet.notification.repository.FailedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DLQConsumer {

    private final FailedMessageRepository failedMessageRepository;

    @KafkaListener(
            topics = "transaction.created.DLQ",
            groupId = "wallet-dlq-group"
    )
    public void consumeDeadLetter(
            @Payload byte[] message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String payload = new String(message);

        log.error("Dead letter received - Topic: {}, Offset: {}, Message: {}",
                topic, offset, payload);

        try {
            if (failedMessageRepository.existsByTopicAndOffset(topic, offset)) {
                log.warn("Duplicate DLQ message skipped - Topic: {}, Offset: {}",
                        topic, offset);
                return;
            }

            FailedMessage failedMessage = FailedMessage.builder()
                    .topic(topic)
                    .offset(offset)
                    .payload(payload)
                    .errorReason("Message failed after 3 retry attempts")
                    .status(FailedMessageStatus.PENDING)
                    .build();

            failedMessageRepository.save(failedMessage);

            log.info("Failed message saved to DB - Topic: {}, Offset: {}",
                    topic, offset);

        } catch (Exception e) {
            log.error("Could not save failed message to DB: {}", e.getMessage());
        }
    }
}