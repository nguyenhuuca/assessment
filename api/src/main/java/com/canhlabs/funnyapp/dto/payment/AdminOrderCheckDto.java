package com.canhlabs.funnyapp.dto.payment;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of looking an order up on SePay: {@code found} undelivered transfers carrying its code,
 * {@code processed} of them newly recorded, and the order as it is now.
 */
@Getter
@Builder
public class AdminOrderCheckDto {
    private int found;
    private int processed;
    private AdminOrderDto order;
}
