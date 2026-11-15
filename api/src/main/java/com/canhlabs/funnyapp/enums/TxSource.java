package com.canhlabs.funnyapp.enums;

/** Which path delivered a provider transfer to us. */
public enum TxSource {
    /** Pushed by the provider webhook. */
    WEBHOOK,
    /** Pulled by the scheduled reconciliation job. */
    RECONCILE
}
