package com.paymentprocessing.wallet.notification.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTransactionEvent(TransactionEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(
                        KafkaTopicConfig.TRANSACTION_TOPIC,
                        event.getReferenceId(),
                        event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Transaction event sent successfully: {} to partition: {}",
                        event.getReferenceId(),
                        result.getRecordMetadata().partition());
            } else {
                log.error("Failed to send transaction event: {}",
                        event.getReferenceId(), ex);
            }
        });
    }
}