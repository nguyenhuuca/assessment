package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.service.SepayWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Check order 503 → 403 → 413 → 401 → 400 → 200 (ADR-0037, sequence §3). */
class SepayWebhookControllerTest {

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    private static final String TS = "1760000000";
    private static final String SIG = "sha256=abc";

    private SepayWebhookService service;
    private SepayWebhookController controller;

    @BeforeEach
    void setUp() {
        service = mock(SepayWebhookService.class);
        controller = new SepayWebhookController();
        controller.injectWebhookService(service);
        when(service.isConfigured()).thenReturn(true);
        when(service.isIpAllowed(any())).thenReturn(true);
    }

    private static MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/funny-app/webhooks/sepay");
        request.setContent(body);
        request.setRemoteAddr("5.6.7.8");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        return request;
    }

    private ResponseEntity<String> call(byte[] body) throws Exception {
        return controller.receive(TS, SIG, request(body));
    }

    @Test
    void noSecret_503_nothingElseTouched() throws Exception {
        when(service.isConfigured()).thenReturn(false);
        assertThat(call(BODY).getStatusCode().value()).isEqualTo(503);
        verify(service, never()).verify(any(), any(), any());
        verify(service, never()).isIpAllowed(any());
    }

    @Test
    void ipNotAllowed_403_usingRemoteAddrNotForwardedHeader() throws Exception {
        when(service.isIpAllowed("5.6.7.8")).thenReturn(false);
        assertThat(call(BODY).getStatusCode().value()).isEqualTo(403);
        verify(service, never()).verify(any(), any(), any());
    }

    @Test
    void oversize_413_beforeSignature() throws Exception {
        assertThat(call(new byte[SepayWebhookController.MAX_BODY + 1]).getStatusCode().value()).isEqualTo(413);
        verify(service, never()).verify(any(), any(), any());
    }

    @Test
    void bodyAtCap_isVerified() throws Exception {
        when(service.verify(any(), any(), any())).thenReturn(false);
        assertThat(call(new byte[SepayWebhookController.MAX_BODY]).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void badSignature_401_nothingProcessed() throws Exception {
        when(service.verify(TS, SIG, BODY)).thenReturn(false);
        assertThat(call(BODY).getStatusCode().value()).isEqualTo(401);
        verify(service, never()).process(any(), any());
    }

    @Test
    void unparsableBody_400() throws Exception {
        when(service.verify(TS, SIG, BODY)).thenReturn(true);
        when(service.parse(BODY)).thenThrow(new IllegalArgumentException("x"));
        assertThat(call(BODY).getStatusCode().value()).isEqualTo(400);
        verify(service, never()).process(any(), any());
    }

    @Test
    void valid_200_exactBody_processedAsWebhook() throws Exception {
        SepayTxPayload tx = new SepayTxPayload(1, null, null, null, null, null, "in", 5, null);
        when(service.verify(TS, SIG, BODY)).thenReturn(true);
        when(service.parse(BODY)).thenReturn(tx);
        ResponseEntity<String> res = call(BODY);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isEqualTo("{\"success\": true}");
        assertThat(res.getHeaders().getContentType().toString()).startsWith("application/json");
        verify(service).process(tx, TxSource.WEBHOOK);
    }
}
