package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.GitHubWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Check order 503 → 413 → 401 → 400 → 204 on a raw body (ADR-0026 D7, spec FR-8). */
class GitHubWebhookControllerTest {

    private static final byte[] BODY = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
    private static final String SIG = "sha256=abc";

    private GitHubWebhookService service;
    private GitHubWebhookController controller;

    @BeforeEach
    void setUp() {
        service = mock(GitHubWebhookService.class);
        controller = new GitHubWebhookController();
        controller.injectWebhookService(service);
    }

    private static MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/funny-app/webhooks/github");
        request.setContent(body);
        return request;
    }

    private int status(byte[] body) throws Exception {
        return controller.receive("issues", "d-1", SIG, request(body)).getStatusCode().value();
    }

    @Test
    void noSecret_returns503_withoutReadingOrVerifying() throws Exception {
        when(service.isConfigured()).thenReturn(false);

        assertThat(status(new byte[GitHubWebhookController.MAX_BODY + 10])).isEqualTo(503);
        verify(service, never()).verify(any(), any());
    }

    @Test
    void oversizeBody_returns413_beforeSignatureCheck() throws Exception {
        when(service.isConfigured()).thenReturn(true);

        assertThat(status(new byte[GitHubWebhookController.MAX_BODY + 1])).isEqualTo(413);
        verify(service, never()).verify(any(), any());
    }

    @Test
    void bodyAtTheCap_isVerified() throws Exception {
        when(service.isConfigured()).thenReturn(true);
        when(service.verify(any(), any())).thenReturn(false);

        assertThat(status(new byte[GitHubWebhookController.MAX_BODY])).isEqualTo(401);
    }

    @Test
    void badSignature_returns401_andNothingHandled() throws Exception {
        when(service.isConfigured()).thenReturn(true);
        when(service.verify(SIG, BODY)).thenReturn(false);

        assertThat(status(BODY)).isEqualTo(401);
        verify(service, never()).handle(any(), any(), any());
    }

    @Test
    void badPayload_returns400() throws Exception {
        when(service.isConfigured()).thenReturn(true);
        when(service.verify(SIG, BODY)).thenReturn(true);
        when(service.handle("issues", "d-1", BODY)).thenThrow(new IllegalArgumentException("bad"));

        assertThat(status(BODY)).isEqualTo(400);
    }

    @Test
    void validDelivery_returns204_withRawBodyPassedThrough() throws Exception {
        when(service.isConfigured()).thenReturn(true);
        when(service.verify(SIG, BODY)).thenReturn(true);
        when(service.handle("issues", "d-1", BODY)).thenReturn(GitHubWebhookService.Outcome.IGNORED);

        assertThat(status(BODY)).isEqualTo(204);
        verify(service).handle("issues", "d-1", BODY);
    }
}
