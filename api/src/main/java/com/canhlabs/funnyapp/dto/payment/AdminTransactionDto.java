package com.canhlabs.funnyapp.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AdminTransactionDto {
    private Long id;
    /** Provider display name, e.g. "SePay". */
    private String provider;
    private long amount;
    private String content;
    private String code;
    private String gateway;
    private Instant transactionDate;
    private Instant receivedAt;
}
