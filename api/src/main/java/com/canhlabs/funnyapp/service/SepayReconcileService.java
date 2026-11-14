package com.canhlabs.funnyapp.service;

import java.time.Instant;

/**
 * Recovers SePay transfers whose webhook never arrived by reading the SePay user API v2 (ADR-0037, PAY-5).
 */
public interface SepayReconcileService {

    /**
     * Scheduled pass: runs when the Harness flag {@code sepay_reconcile_enabled} is on and the API token is set.
     * Transfers already stored are skipped; each new one goes through the webhook path with source RECONCILE.
     * API failures are logged, never thrown. Returns how many transfers were processed.
     */
    int reconcile();

    /**
     * Admin action: looks up one order code on SePay right now, from {@code since} (order creation), and processes
     * any transfer the webhook did not deliver. Not gated by the flag, only by the API token.
     *
     * @throws SepayApiException when the token is missing or SePay cannot be read
     */
    CheckResult checkCode(String code, Instant since);

    /** {@code found}: undelivered transfers carrying the code; {@code processed}: newly sent through the webhook path. */
    record CheckResult(int found, int processed) {
    }

    /** SePay could not be asked: {@code configured} is false when the API token is missing. */
    class SepayApiException extends RuntimeException {
        private final boolean configured;

        public SepayApiException(String message, boolean configured) {
            super(message);
            this.configured = configured;
        }

        public boolean isConfigured() {
            return configured;
        }
    }
}
