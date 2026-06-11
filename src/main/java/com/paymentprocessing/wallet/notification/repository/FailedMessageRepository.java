package com.paymentprocessing.wallet.notification.repository;

import com.paymentprocessing.wallet.notification.entity.FailedMessage;
import com.paymentprocessing.wallet.notification.entity.FailedMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedMessageRepository extends JpaRepository<FailedMessage, Long> {

    List<FailedMessage> findByStatus(FailedMessageStatus status);

    List<FailedMessage> findByTopic(String topic);

    boolean existsByTopicAndOffset(String topic, Long offset);
}