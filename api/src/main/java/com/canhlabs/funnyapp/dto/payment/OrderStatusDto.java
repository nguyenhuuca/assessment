package com.canhlabs.funnyapp.dto.payment;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Public view of an order: no donor, user or message data. */
@Getter
@Builder
public class OrderStatusDto {
    private String code;
    private PaymentType type;
    private PaymentOrderStatus status;
    /** Amount paid once money arrived, else the expected amount (null for a donation with nothing paid yet). */
    private Long amount;
    private Instant expiresAt;
}
