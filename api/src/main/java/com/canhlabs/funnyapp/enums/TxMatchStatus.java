package com.canhlabs.funnyapp.enums;

/** How a SePay transaction relates to our orders. */
public enum TxMatchStatus {
    /** Memo code matched a payment order. */
    MATCHED,
    /** Incoming transfer with no matching order; waits for admin review. */
    UNMATCHED,
    /** Not relevant (e.g. outgoing transfer) or dismissed by an admin. */
    IGNORED
}
