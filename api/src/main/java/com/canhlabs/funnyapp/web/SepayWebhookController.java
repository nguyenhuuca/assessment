package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.service.SepayWebhookService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * SePay bank-transfer webhook (ADR-0037). No JWT: the HMAC signature is the authentication.
 * Check order: 503 (no secret) → 403 (IP allowlist) → 413 → 401 → 400 → 200.
 */
@Slf4j
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/webhooks/sepay")
public class SepayWebhookController {

    /** A transaction payload is under 2 KB; anything over 64 KiB is refused before the HMAC (CWE-770). */
    static final int MAX_BODY = 64 * 1024;
    static final String OK_BODY = "{\"success\": true}";

    private SepayWebhookService webhookService;

    @Autowired
    public void injectWebhookService(SepayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<String> receive(
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            HttpServletRequest request) throws IOException {
        if (!webhookService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        // getRemoteAddr only: X-Forwarded-For is client-controlled and there is no trusted-proxy helper
        if (!webhookService.isIpAllowed(request.getRemoteAddr())) {
            log.warn("SePay webhook rejected: source address not allowed");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!webhookService.verify(timestamp, signature, body)) {
            // fixed text only: no header or body content (probing, stale replay or a mismatched secret)
            log.warn("SePay webhook rejected: bad, stale or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SepayTxPayload tx;
        try {
            tx = webhookService.parse(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        webhookService.process(tx, TxSource.WEBHOOK);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(OK_BODY);
    }
}
