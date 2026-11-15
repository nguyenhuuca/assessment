package com.canhlabs.funnyapp.service.payment;

import java.util.Optional;

/**
 * Port for a bank-transfer payment vendor (ADR-0038). Everything vendor-specific lives behind it: webhook
 * authentication and payload shape, the pull API, and the QR image. Orders, matching, settlement, admin and the
 * frontend only see this interface, so switching vendor means adding one implementation and its configuration.
 */
public interface PaymentProvider {

    /** Stable lower-case id stored on orders and transactions, and used in the webhook URL. */
    String id();

    /** Name for logs and the admin UI, e.g. "SePay". */
    String displayName();

    /** QR URL and bank fields for one order. */
    PaymentInstructions instructions(String orderCode, long amountVnd);

    /** Authenticates and parses one webhook. Never touches the database and never throws on bad input. */
    WebhookResult receive(WebhookRequest request);

    /** The vendor's pull API, if it has one. */
    Optional<TransferFeed> feed();
}
