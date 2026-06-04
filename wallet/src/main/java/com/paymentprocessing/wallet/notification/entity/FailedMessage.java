package com.paymentprocessing.wallet.notification.entity;

import com.paymentprocessing.wallet.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "failed_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedMessage extends BaseEntity {

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Long offset;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String errorReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FailedMessageStatus status = FailedMessageStatus.PENDING;
}