package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.service.PaymentIngestionService;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.PaymentProvider;
import com.canhlabs.funnyapp.service.payment.PaymentProviderRegistry;
import com.canhlabs.funnyapp.service.payment.WebhookRequest;
import com.canhlabs.funnyapp.service.payment.WebhookResult;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Payment provider webhooks (ADR-0037, ADR-0038). No JWT: each provider authenticates its own calls.
 * Check order: 404 (unknown provider) → 413 → whatever the provider refuses with (503, 403, 401, 400) → 200.
 * {@code /webhooks/sepay} stays as an alias because that URL is configured in the SePay dashboard.
 */
@Slf4j
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/webhooks")
public class PaymentWebhookController {

    /** A transaction payload is under 2 KB; anything over 64 KiB is refused before authentication (CWE-770). */
    static final int MAX_BODY = 64 * 1024;

    private PaymentProviderRegistry registry;
    private PaymentIngestionService ingestion;

    @Autowired
    public void inject(PaymentProviderRegistry registry, PaymentIngestionService ingestion) {
        this.registry = registry;
        this.ingestion = ingestion;
    }

    @PostMapping("/payments/{provider}")
    public ResponseEntity<String> receive(@PathVariable String provider, HttpServletRequest request) throws IOException {
        Optional<PaymentProvider> found = registry.byId(provider);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        PaymentProvider p = found.get();
        WebhookResult result = p.receive(new WebhookRequest(headers(request), body, request.getRemoteAddr()));
        return switch (result) {
            case WebhookResult.Rejected r -> {
                // fixed text only: no header or body content
                log.warn("{} webhook rejected ({}): {}", p.displayName(), r.httpStatus(), r.reason());
                yield ResponseEntity.status(r.httpStatus()).build();
            }
            case WebhookResult.Accepted a -> {
                for (BankTransfer tx : a.transfers()) {
                    ingestion.process(tx, TxSource.WEBHOOK);
                }
                yield ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(a.ackBody());
            }
        };
    }

    /** Old SePay URL, still set in the SePay dashboard. */
    @PostMapping("/sepay")
    public ResponseEntity<String> receiveSepay(HttpServletRequest request) throws IOException {
        return receive("sepay", request);
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
