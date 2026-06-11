package com.paymentprocessing.wallet.wallet.entity;

import com.paymentprocessing.wallet.common.entity.BaseEntity;
import com.paymentprocessing.wallet.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    // ✅ Optimistic locking: JPA increments this version on every UPDATE.
    // If two concurrent transactions read the same version and both try to
    // commit, the second one gets an ObjectOptimisticLockingFailureException,
    // which is caught by GlobalExceptionHandler and returned as HTTP 409.
    @Version
    private Long version;
}