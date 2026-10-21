package com.canhlabs.funnyapp.service;

/** Receives the GitHub repo webhook and stores the issue state of in-app reports (ADR-0026 D7). */
public interface GitHubWebhookService {

    enum Outcome { APPLIED, IGNORED, STALE, UNKNOWN_ISSUE }

    /** False while {@code GITHUB_WEBHOOK_SECRET} is blank: the endpoint is off. */
    boolean isConfigured();

    /** True when {@code X-Hub-Signature-256} is {@code sha256=<hex HMAC of body>}; constant-time compare. */
    boolean verify(String signatureHeader, byte[] body);

    /**
     * Applies a verified delivery. Throws {@link IllegalArgumentException} when the body is not JSON,
     * or when an {@code issues} event lacks the issue number or a valid {@code updated_at}.
     */
    Outcome handle(String event, String deliveryId, byte[] body);
}
