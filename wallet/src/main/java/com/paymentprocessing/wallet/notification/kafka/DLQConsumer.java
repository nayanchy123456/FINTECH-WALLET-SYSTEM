package com.paymentprocessing.wallet.notification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DLQConsumer {

    @KafkaListener(
            topics = "transaction.created.DLQ",
            groupId = "wallet-dlq-group"
    )
    public void consumeDeadLetter(
            @Payload byte[] message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.error("Dead letter received - Topic: {}, Offset: {}, Message: {}",
                topic, offset, new String(message));

        // In production: save to DB, alert team, manual review
    }
}