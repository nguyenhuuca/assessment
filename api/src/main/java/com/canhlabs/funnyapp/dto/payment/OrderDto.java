package com.canhlabs.funnyapp.dto.payment;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Returned on creation: everything the client needs to show the QR and the manual transfer details. */
@Getter
@Builder
public class OrderDto {
    private String code;
    private PaymentType type;
    private PaymentOrderStatus status;
    private long amount;
    private Instant expiresAt;
    private String qrUrl;
    private String bankCode;
    private String bankAccount;
    private String accountName;
    /** Transfer memo; equals {@code code}. */
    private String memo;
}
