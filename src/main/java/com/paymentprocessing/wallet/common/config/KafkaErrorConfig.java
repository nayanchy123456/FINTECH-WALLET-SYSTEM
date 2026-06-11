package com.paymentprocessing.wallet.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorConfig {

    private static final String DLQ_SUFFIX = ".DLQ";
    private static final long RETRY_INTERVAL = 1000L;
    private static final long MAX_ATTEMPTS = 3L;

    @Bean
    public CommonErrorHandler errorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate,
                        (record, ex) -> {
                            log.error("Message failed after retries. " +
                                    "Sending to DLQ. Topic: {}, Error: {}",
                                    record.topic(), ex.getMessage());
                            return new org.apache.kafka.common.TopicPartition(
                                    record.topic() + DLQ_SUFFIX,
                                    record.partition());
                        });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL, MAX_ATTEMPTS));

        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                NullPointerException.class);

        return errorHandler;
    }
}