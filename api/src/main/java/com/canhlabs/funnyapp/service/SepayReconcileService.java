package com.canhlabs.funnyapp.service;

/**
 * Recovers SePay transfers whose webhook never arrived (server down past SePay's retry window) by
 * reading recent incoming transactions from the SePay user API (ADR-0037, PAY-5).
 */
public interface SepayReconcileService {

    /**
     * Runs one pass when the Harness flag {@code sepay_reconcile_enabled} is on and the API token is set.
     * Transfers already stored (webhook or an earlier pass) are skipped; each new one goes through the
     * webhook path with source RECONCILE. Returns how many transfers were recovered.
     */
    int reconcile();
}
