package com.canhlabs.funnyapp.service.payment;

import java.util.List;

/** Outcome of {@link PaymentProvider#receive}: transfers to ingest, or the HTTP status to refuse with. */
public sealed interface WebhookResult {

    /** Authenticated and parsed; {@code ackBody} is the JSON the provider expects back with HTTP 200. */
    record Accepted(List<BankTransfer> transfers, String ackBody) implements WebhookResult {
    }

    /** Refused before anything is stored. {@code reason} is a fixed text, safe to log. */
    record Rejected(int httpStatus, String reason) implements WebhookResult {
    }
}
