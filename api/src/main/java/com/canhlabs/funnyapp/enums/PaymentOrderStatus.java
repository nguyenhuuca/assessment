package com.canhlabs.funnyapp.enums;

/** Lifecycle of a payment order; PENDING is the only state that can still receive money. */
public enum PaymentOrderStatus {
    /** QR shown, no matching transfer yet. */
    PENDING,
    /** Transfer(s) cover the expected amount. */
    PAID,
    /** Transfer received but below the expected amount; needs admin action. */
    UNDERPAID,
    /** Expiry passed without payment. */
    EXPIRED,
    /** Cancelled by the user or an admin. */
    CANCELLED,
    /** Underpaid or late order settled manually by an admin. */
    RESOLVED
}
