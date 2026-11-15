package com.canhlabs.funnyapp.service.payment.sepay;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.config.JsonMappers;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.PaymentInstructions;
import com.canhlabs.funnyapp.service.payment.PaymentProvider;
import com.canhlabs.funnyapp.service.payment.PaymentProviderContractTest;
import com.canhlabs.funnyapp.service.payment.WebhookRequest;
import com.canhlabs.funnyapp.service.payment.WebhookResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SepayProviderTest extends PaymentProviderContractTest {

    private static final String SECRET = "sepay-test-secret";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String CODE = "FMABCD1234";
    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    private static final String TS = String.valueOf(NOW.getEpochSecond());

    private AppProperties props;
    private SepayTransferFeed feed;
    private SepayProvider provider;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getPayment().getSepay().setWebhookSecret(SECRET);
        feed = mock(SepayTransferFeed.class);
        provider = new SepayProvider(props, JsonMappers.create(), feed, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Override
    protected PaymentProvider provider() {
        return provider;
    }

    private static String sign(String secret, String ts, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static WebhookRequest signed(byte[] body, String remoteAddr) throws Exception {
        return new WebhookRequest(Map.of("x-sepay-timestamp", TS, "X-SEPAY-SIGNATURE", sign(SECRET, TS, body)),
                body, remoteAddr);
    }

    // ---- identity / instructions ----

    @Test
    void identity() {
        assertThat(provider.id()).isEqualTo("sepay");
        assertThat(provider.displayName()).isEqualTo("SePay");
        assertThat(provider.feed()).containsSame(feed);
    }

    @Test
    void instructions_encodeEveryQrParameter_andCarryBankFields() {
        props.getPayment().getSepay().setQrBaseUrl("https://qr.example/img");
        props.getPayment().setBankAccount("0123 456");
        props.getPayment().setBankCode("MB&Bank");
        props.getPayment().setAccountName("NGUYEN VAN A");

        PaymentInstructions pay = provider.instructions("FMAB12CD34", 50000);

        assertThat(pay.qrUrl()).isEqualTo(
                "https://qr.example/img?acc=0123+456&bank=MB%26Bank&amount=50000&des=FMAB12CD34&template=compact");
        assertThat(pay.bankAccount()).isEqualTo("0123 456");
        assertThat(pay.bankCode()).isEqualTo("MB&Bank");
        assertThat(pay.accountName()).isEqualTo("NGUYEN VAN A");
    }

    // ---- receive: check order 503 → 403 → 401 → 400 → accepted ----

    @Test
    void receive_noSecret_503() throws Exception {
        WebhookRequest req = signed(BODY, "1.1.1.1");
        props.getPayment().getSepay().setWebhookSecret("");
        assertThat(provider.receive(req)).isEqualTo(new WebhookResult.Rejected(503, "webhook secret not set"));
    }

    @Test
    void receive_ipNotAllowed_403_beforeSignature() {
        props.getPayment().getSepay().setAllowedIps("2.2.2.2");
        WebhookRequest unsigned = new WebhookRequest(Map.of(), BODY, "5.6.7.8");
        assertThat(provider.receive(unsigned)).isInstanceOf(WebhookResult.Rejected.class)
                .extracting(r -> ((WebhookResult.Rejected) r).httpStatus()).isEqualTo(403);
    }

    @Test
    void receive_badSignature_401() {
        WebhookRequest req = new WebhookRequest(Map.of("X-SePay-Timestamp", TS, "X-SePay-Signature", "sha256=abc"),
                BODY, "1.1.1.1");
        assertThat(((WebhookResult.Rejected) provider.receive(req)).httpStatus()).isEqualTo(401);
    }

    @Test
    void receive_signedButUnparsable_400() throws Exception {
        assertThat(((WebhookResult.Rejected) provider.receive(signed(BODY, "1.1.1.1"))).httpStatus()).isEqualTo(400);
    }

    @Test
    void receive_valid_acceptedWithAck_headersCaseInsensitive() throws Exception {
        byte[] body = "{\"id\":7,\"transferType\":\"in\",\"transferAmount\":5000,\"content\":\"FMABCD1234\"}"
                .getBytes(StandardCharsets.UTF_8);
        WebhookResult result = provider.receive(signed(body, "1.1.1.1"));
        assertThat(result).isInstanceOf(WebhookResult.Accepted.class);
        WebhookResult.Accepted ok = (WebhookResult.Accepted) result;
        assertThat(ok.ackBody()).isEqualTo("{\"success\": true}");
        assertThat(ok.transfers()).singleElement().satisfies(t -> {
            assertThat(t.provider()).isEqualTo("sepay");
            assertThat(t.providerTxId()).isEqualTo("7");
            assertThat(t.amount()).isEqualTo(5000);
        });
    }

    // ---- verify ----

    @Test
    void verify_validSignature_passes() throws Exception {
        assertThat(provider.verify(TS, sign(SECRET, TS, BODY), BODY)).isTrue();
    }

    @Test
    void verify_edgeOfWindow_passesAndBeyondFails() throws Exception {
        String old = String.valueOf(NOW.getEpochSecond() - 300);
        String tooOld = String.valueOf(NOW.getEpochSecond() - 301);
        String future = String.valueOf(NOW.getEpochSecond() + 301);
        assertThat(provider.verify(old, sign(SECRET, old, BODY), BODY)).isTrue();
        assertThat(provider.verify(tooOld, sign(SECRET, tooOld, BODY), BODY)).isFalse();
        assertThat(provider.verify(future, sign(SECRET, future, BODY), BODY)).isFalse();
    }

    @Test
    void verify_wrongSecretOrTamperedBodyOrTimestamp_fails() throws Exception {
        assertThat(provider.verify(TS, sign("other", TS, BODY), BODY)).isFalse();
        assertThat(provider.verify(TS, sign(SECRET, TS, BODY), "{\"id\":2}".getBytes(StandardCharsets.UTF_8))).isFalse();
        String shifted = String.valueOf(NOW.getEpochSecond() + 1);
        assertThat(provider.verify(shifted, sign(SECRET, TS, BODY), BODY)).isFalse();
    }

    @Test
    void verify_malformedInputs_fail() throws Exception {
        String good = sign(SECRET, TS, BODY);
        assertThat(provider.verify(null, good, BODY)).isFalse();
        assertThat(provider.verify(TS, null, BODY)).isFalse();
        assertThat(provider.verify(TS, good, null)).isFalse();
        assertThat(provider.verify("abc", good, BODY)).isFalse();
        assertThat(provider.verify(TS, good.substring(7), BODY)).isFalse();
        assertThat(provider.verify(TS, "sha256=", BODY)).isFalse();
    }

    @Test
    void verify_uppercaseHexSignature_passes() throws Exception {
        String upper = "sha256=" + sign(SECRET, TS, BODY).substring(7).toUpperCase();
        assertThat(provider.verify(TS, upper, BODY)).isTrue();
    }

    @Test
    void verify_noSecret_fails() throws Exception {
        String good = sign(SECRET, TS, BODY);
        props.getPayment().getSepay().setWebhookSecret("");
        assertThat(provider.verify(TS, good, BODY)).isFalse();
    }

    @Test
    void ipAllowlist_offAllowsAll_onAllowsListedOnly() {
        assertThat(provider.isIpAllowed("9.9.9.9")).isTrue();
        assertThat(provider.isIpAllowed(null)).isTrue();
        props.getPayment().getSepay().setAllowedIps(" 1.1.1.1 , 2.2.2.2");
        assertThat(provider.isIpAllowed("2.2.2.2")).isTrue();
        assertThat(provider.isIpAllowed("9.9.9.9")).isFalse();
        assertThat(provider.isIpAllowed(null)).isFalse();
    }

    // ---- parse ----

    @Test
    void parse_fullPayload() {
        String json = """
                {"id":92704,"gateway":"MBBank","transactionDate":"2026-11-12 17:00:00","accountNumber":"0123456789",
                 "code":"FMABCD1234","content":"chuyen tien FMABCD1234","transferType":"in","transferAmount":50000,
                 "referenceCode":"REF1","accumulated":1}""";
        BankTransfer tx = provider.parse(json.getBytes(StandardCharsets.UTF_8));
        assertThat(tx.provider()).isEqualTo("sepay");
        assertThat(tx.providerTxId()).isEqualTo("92704");
        assertThat(tx.amount()).isEqualTo(50000L);
        assertThat(tx.direction()).isEqualTo(TransferDirection.IN);
        assertThat(tx.bank()).isEqualTo("MBBank");
        assertThat(tx.accountNumber()).isEqualTo("0123456789");
        assertThat(tx.code()).isEqualTo(CODE);
        assertThat(tx.content()).isEqualTo("chuyen tien FMABCD1234");
        assertThat(tx.referenceCode()).isEqualTo("REF1");
        assertThat(tx.transactionDate()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
    }

    @Test
    void parse_outAndOptionalFieldsMissingOrBadDate() {
        BankTransfer tx = provider.parse(
                "{\"id\":5,\"transferType\":\"out\",\"transferAmount\":1,\"code\":null,\"transactionDate\":\"nope\"}"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(tx.direction()).isEqualTo(TransferDirection.OUT);
        assertThat(tx.code()).isNull();
        assertThat(tx.content()).isNull();
        assertThat(tx.transactionDate()).isNull();
        assertThat(provider.parse("{\"id\":5,\"transferType\":\"IN\",\"transferAmount\":1}"
                .getBytes(StandardCharsets.UTF_8)).direction()).isEqualTo(TransferDirection.IN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1]", "null", "{}", "{\"id\":\"x\",\"transferType\":\"in\",\"transferAmount\":1}",
            "{\"id\":1,\"transferAmount\":1}", "{\"id\":1,\"transferType\":\"in\"}",
            "{\"id\":1.5,\"transferType\":\"in\",\"transferAmount\":1}",
            "{\"id\":1,\"transferType\":\"in\",\"transferAmount\":1.5}",
            "{\"id\":1,\"transferType\":\"in\",\"transferAmount\":99999999999999999999}"})
    void parse_invalid_throws(String json) {
        assertThatThrownBy(() -> provider.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicConstructor_usesSystemClock() {
        SepayProvider p = new SepayProvider(props, JsonMappers.create(), feed);
        assertThat(p.verify(TS, "sha256=00", BODY)).isFalse();
    }
}
