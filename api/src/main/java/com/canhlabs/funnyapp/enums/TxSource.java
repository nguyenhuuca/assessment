package com.canhlabs.funnyapp.enums;

/** Which path delivered a SePay transaction to us. */
public enum TxSource {
    /** Pushed by the SePay webhook. */
    WEBHOOK,
    /** Pulled by the scheduled reconciliation job. */
    RECONCILE
}
