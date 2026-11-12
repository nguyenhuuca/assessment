package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.enums.TxSource;

/** SePay webhook: HMAC verification and transfer-to-order matching (ADR-0037). */
public interface SepayWebhookService {

    /** False while {@code SEPAY_WEBHOOK_SECRET} is blank: the endpoint is off. */
    boolean isConfigured();

    /** True when the remote address passes {@code app.sepay.allowed-ips}; always true when that list is empty. */
    boolean isIpAllowed(String remoteAddr);

    /**
     * True when {@code signatureHeader} is {@code sha256=<hex HMAC-SHA256(secret, timestamp + "." + body)>},
     * the timestamp (unix seconds) is within 300 s of now, and the compare is constant-time.
     */
    boolean verify(String timestampHeader, String signatureHeader, byte[] body);

    /** Parses a webhook body. Throws {@link IllegalArgumentException} when a required field is missing or invalid. */
    SepayTxPayload parse(byte[] body);

    /** Stores the transaction once and applies it to the matching order. Safe to call again for the same id. */
    void process(SepayTxPayload tx, TxSource source);
}
