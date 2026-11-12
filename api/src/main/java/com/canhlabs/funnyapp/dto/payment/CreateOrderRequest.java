package com.canhlabs.funnyapp.dto.payment;

import com.canhlabs.funnyapp.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private PaymentType type;
    /** DONATION only: amount in VND. */
    private Long amount;
    /** PREMIUM only: id from {@code GET /payments/plans}. */
    private Long planId;
    private String donorName;
    private String message;
}
