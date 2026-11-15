package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.service.PaymentIngestionService;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.PaymentProvider;
import com.canhlabs.funnyapp.service.payment.PaymentProviderRegistry;
import com.canhlabs.funnyapp.service.payment.WebhookRequest;
import com.canhlabs.funnyapp.service.payment.WebhookResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Check order 404 → 413 → provider refusal → 200 (ADR-0038). */
class PaymentWebhookControllerTest {

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);

    private PaymentProvider provider;
    private PaymentIngestionService ingestion;
    private PaymentWebhookController controller;

    @BeforeEach
    void setUp() {
        provider = mock(PaymentProvider.class);
        when(provider.id()).thenReturn("sepay");
        when(provider.displayName()).thenReturn("SePay");
        ingestion = mock(PaymentIngestionService.class);
        AppProperties props = new AppProperties();
        controller = new PaymentWebhookController();
        controller.inject(new PaymentProviderRegistry(List.of(provider), props), ingestion);
    }

    private static MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/funny-app/webhooks/payments/sepay");
        request.setContent(body);
        request.setRemoteAddr("5.6.7.8");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        request.addHeader("X-SePay-Signature", "sha256=abc");
        return request;
    }

    @Test
    void unknownProvider_404_bodyNotRead() throws Exception {
        assertThat(controller.receive("nope", request(BODY)).getStatusCode().value()).isEqualTo(404);
        verify(provider, never()).receive(any());
        verifyNoInteractions(ingestion);
    }

    @Test
    void oversize_413_beforeProvider() throws Exception {
        assertThat(controller.receive("sepay", request(new byte[PaymentWebhookController.MAX_BODY + 1]))
                .getStatusCode().value()).isEqualTo(413);
        verify(provider, never()).receive(any());
    }

    @Test
    void bodyAtCap_reachesProvider_withHeadersBodyAndRemoteAddr() throws Exception {
        when(provider.receive(any())).thenReturn(new WebhookResult.Rejected(401, "bad signature"));
        byte[] body = new byte[PaymentWebhookController.MAX_BODY];

        assertThat(controller.receive("sepay", request(body)).getStatusCode().value()).isEqualTo(401);

        ArgumentCaptor<WebhookRequest> cap = ArgumentCaptor.forClass(WebhookRequest.class);
        verify(provider).receive(cap.capture());
        assertThat(cap.getValue().body()).hasSize(PaymentWebhookController.MAX_BODY);
        assertThat(cap.getValue().remoteAddr()).isEqualTo("5.6.7.8");
        assertThat(cap.getValue().header("x-sepay-signature")).isEqualTo("sha256=abc");
        verifyNoInteractions(ingestion);
    }

    @Test
    void providerRejection_isPassedThrough_nothingIngested() throws Exception {
        for (int status : new int[]{503, 403, 401, 400}) {
            when(provider.receive(any())).thenReturn(new WebhookResult.Rejected(status, "x"));
            assertThat(controller.receive("sepay", request(BODY)).getStatusCode().value()).isEqualTo(status);
        }
        verifyNoInteractions(ingestion);
    }

    @Test
    void accepted_200_ackBody_everyTransferIngestedAsWebhook() throws Exception {
        BankTransfer a = new BankTransfer("sepay", "1", TransferDirection.IN, 5, null, null, null, null, null, null);
        BankTransfer b = new BankTransfer("sepay", "2", TransferDirection.IN, 6, null, null, null, null, null, null);
        when(provider.receive(any())).thenReturn(new WebhookResult.Accepted(List.of(a, b), "{\"success\": true}"));

        ResponseEntity<String> res = controller.receive("SEPAY", request(BODY));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isEqualTo("{\"success\": true}");
        assertThat(res.getHeaders().getContentType().toString()).startsWith("application/json");
        verify(ingestion).process(a, TxSource.WEBHOOK);
        verify(ingestion).process(b, TxSource.WEBHOOK);
    }

    @Test
    void oldSepayUrl_routesToSepayProvider() throws Exception {
        when(provider.receive(any())).thenReturn(new WebhookResult.Accepted(List.of(), "{\"success\": true}"));
        assertThat(controller.receiveSepay(request(BODY)).getStatusCode().value()).isEqualTo(200);
        verify(provider).receive(any());
    }
}
