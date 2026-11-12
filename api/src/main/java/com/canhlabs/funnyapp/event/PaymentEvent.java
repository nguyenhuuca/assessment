package com.canhlabs.funnyapp.event;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;

/**
 * Published when a transfer moved an order into PAID or UNDERPAID (ADR-0037). {@code amount} is the VND value
 * of the transfer that caused the change, not the order total.
 */
public record PaymentEvent(Long orderId, PaymentType type, PaymentOrderStatus status, long amount) {
}
