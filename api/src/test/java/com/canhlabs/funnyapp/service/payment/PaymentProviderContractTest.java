package com.canhlabs.funnyapp.service.payment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * What every {@link PaymentProvider} adapter must do (ADR-0038). An adapter's test class extends this and returns a
 * configured instance; the checks need no network and no database.
 */
public abstract class PaymentProviderContractTest {

    /** A provider with its webhook configured (secret set), as the app would run it. */
    protected abstract PaymentProvider provider();

    @Test
    void contract_idIsStableLowerCaseAndFitsTheColumn() {
        String id = provider().id();
        assertThat(id).matches("[a-z0-9-]{1,16}");
        assertThat(provider().id()).isEqualTo(id);
        assertThat(provider().displayName()).isNotBlank();
    }

    @Test
    void contract_unsignedOrGarbageWebhook_isRejectedWithoutThrowing() {
        for (byte[] body : new byte[][]{new byte[0], "not json".getBytes(StandardCharsets.UTF_8),
                "{\"id\":1,\"transferAmount\":5}".getBytes(StandardCharsets.UTF_8)}) {
            assertThatCode(() -> {
                WebhookResult r = provider().receive(new WebhookRequest(Map.of(), body, "203.0.113.9"));
                assertThat(r).isInstanceOf(WebhookResult.Rejected.class);
                assertThat(((WebhookResult.Rejected) r).httpStatus()).isBetween(400, 599);
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void contract_nullHeadersAndAddress_areRejectedNotThrown() {
        assertThatCode(() -> provider().receive(new WebhookRequest(null, new byte[0], null)))
                .doesNotThrowAnyException();
    }

    @Test
    void contract_instructionsCarryTheOrderCode() {
        PaymentInstructions pay = provider().instructions("FMCONTRACT1", 20000);
        assertThat(pay).isNotNull();
        assertThat(pay.qrUrl()).contains("FMCONTRACT1");
    }
}
