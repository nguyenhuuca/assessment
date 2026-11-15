package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.service.payment.ProviderApiException;

import java.time.Instant;

/**
 * Recovers transfers whose webhook never arrived by reading each provider's pull API (ADR-0037 PAY-5, ADR-0038).
 */
public interface PaymentReconcileService {

    /**
     * Scheduled pass over the previous calendar day (T-1, Vietnam time) for every provider with a configured feed;
     * runs only while the Harness flag {@code sepay_reconcile_enabled} is on. Transfers already stored are skipped;
     * each new one goes through ingestion with source RECONCILE. API failures are logged, never thrown.
     * Returns how many transfers were processed.
     */
    int reconcile();

    /**
     * Admin action: looks up one order code on its provider right now, from {@code since}, and processes any
     * transfer the webhook did not deliver. Not gated by the flag.
     *
     * @throws ProviderApiException when the provider has no configured feed or cannot be read
     */
    CheckResult checkCode(String provider, String code, Instant since);

    /** {@code found}: undelivered transfers carrying the code; {@code processed}: newly sent through ingestion. */
    record CheckResult(int found, int processed) {
    }
}
