package com.canhlabs.funnyapp.service.payment.sepay;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.config.JsonMappers;
import com.canhlabs.funnyapp.service.payment.FeedTransfer;
import com.canhlabs.funnyapp.service.payment.ProviderApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Paging, filtering and HTTP errors are covered end to end in PaymentReconcileServiceImplTest. */
class SepayTransferFeedTest {

    private final JsonMapper mapper = JsonMappers.create();

    @Test
    void apiBase_usesConfiguredHost_elseLive() {
        AppProperties props = new AppProperties();
        SepayTransferFeed feed = new SepayTransferFeed(props, mapper);

        props.getPayment().getSepay().setApiBaseUrl(" https://userapi-sandbox.sepay.vn// ");
        assertThat(feed.apiBase()).isEqualTo("https://userapi-sandbox.sepay.vn");
        props.getPayment().getSepay().setApiBaseUrl(" ");
        assertThat(feed.apiBase()).isEqualTo("https://userapi.sepay.vn");
        props.getPayment().getSepay().setApiBaseUrl(null);
        assertThat(feed.apiBase()).isEqualTo(SepayTransferFeed.DEFAULT_API_BASE);
    }

    @Test
    void noToken_isNotConfigured_andFetchThrowsWithoutCalling() {
        AppProperties props = new AppProperties();
        SepayTransferFeed feed = new SepayTransferFeed(props, mapper);

        assertThat(feed.isConfigured()).isFalse();
        assertThatThrownBy(() -> feed.fetch("FM", Instant.EPOCH, Instant.now()))
                .isInstanceOfSatisfying(ProviderApiException.class, e -> assertThat(e.isConfigured()).isFalse());
    }

    @Test
    void toTransfer_keepsUuidLowerCased_andDeliveredFlag() {
        FeedTransfer t = SepayTransferFeed.toTransfer(mapper.readTree("""
                {"id":"A1B2C3D4-E5F6-7890-ABCD-EF1234567890","transaction_date":"2026-11-14 13:30:00",
                 "transfer_type":"in","amount_in":5000,"transaction_content":"FMAAAAAAAA","webhook_success":1}"""));

        assertThat(t.transfer().providerTxId()).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(t.transfer().provider()).isEqualTo("sepay");
        assertThat(t.transfer().transactionDate()).isEqualTo(Instant.parse("2026-11-14T06:30:00Z"));
        assertThat(t.deliveredByWebhook()).isTrue();
    }

    @Test
    void toTransfer_outgoingRow_isDropped() {
        assertThat(SepayTransferFeed.toTransfer(mapper.readTree("""
                {"id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","transaction_date":"2026-11-14 13:30:00",
                 "transfer_type":"out","amount_in":5000}"""))).isNull();
    }

    @Test
    void sepayTime_roundTrip() {
        Instant at = Instant.parse("2026-11-14T06:30:00Z");
        assertThat(SepayTime.format(at)).isEqualTo("2026-11-14 13:30:00");
        assertThat(SepayTime.parse(SepayTime.format(at))).isEqualTo(at);
        assertThat(SepayTime.parse(null)).isNull();
    }
}
