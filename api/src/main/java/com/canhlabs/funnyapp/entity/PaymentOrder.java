package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/** One payment order per QR shown; its code goes into the bank transfer memo. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends BaseDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PaymentType type;

    /** Null for anonymous donations or after the account is deleted. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "plan_id")
    private Long planId;

    /** Null for free-amount donations. */
    @Column(name = "expected_amount")
    private Long expectedAmount;

    @Column(name = "paid_amount", nullable = false)
    @Builder.Default
    private long paidAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    @Column(name = "donor_name", length = 50)
    private String donorName;

    @Column(name = "message", length = 200)
    private String message;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
