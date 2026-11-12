package com.canhlabs.funnyapp.dto.payment;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Admin row for a payment order. {@code surplus} is the overpayment on PREMIUM orders, 0 otherwise. */
@Getter
@Builder
public class AdminOrderDto {
    private Long id;
    private String code;
    private PaymentType type;
    private PaymentOrderStatus status;
    private Long expectedAmount;
    private long paidAmount;
    private long surplus;
    private String donorName;
    private String message;
    private Long userId;
    private String planCode;
    private Instant createdAt;
    private Instant paidAt;
    private Instant expiresAt;
}
