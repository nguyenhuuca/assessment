package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.service.payment.BankTransfer;

/**
 * Vendor-neutral path every reported transfer goes through (ADR-0038): store once, find the order by the code
 * in the memo, and settle it. Shared by provider webhooks, reconciliation and the admin "check order" action.
 */
public interface PaymentIngestionService {

    /** Stores the transfer once and applies it to the matching order. Safe to call again for the same transfer. */
    void process(BankTransfer transfer, TxSource source);
}
