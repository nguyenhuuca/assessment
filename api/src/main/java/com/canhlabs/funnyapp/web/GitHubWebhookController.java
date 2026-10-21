package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.GitHubWebhookService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * GitHub repo webhook for issue state (ADR-0026 D7). No JWT: the HMAC signature is the authentication.
 * Check order: 503 (no secret, body not read) → 413 → 401 → 400 → 204.
 */
@Slf4j
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/webhooks/github")
public class GitHubWebhookController {

    /** {@code issues} payloads are tens of KB; anything larger is refused before the HMAC (CWE-770). */
    static final int MAX_BODY = 1024 * 1024;

    private GitHubWebhookService webhookService;

    @Autowired
    public void injectWebhookService(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            HttpServletRequest request) throws IOException {
        if (!webhookService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!webhookService.verify(signature, body)) {
            // fixed text only: no header or body content (probing or a mismatched secret)
            log.warn("GitHub webhook rejected: bad or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            webhookService.handle(event, deliveryId, body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }
}
