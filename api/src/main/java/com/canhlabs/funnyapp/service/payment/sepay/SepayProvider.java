package com.canhlabs.funnyapp.service.payment.sepay;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.PaymentInstructions;
import com.canhlabs.funnyapp.service.payment.PaymentProvider;
import com.canhlabs.funnyapp.service.payment.TransferFeed;
import com.canhlabs.funnyapp.service.payment.WebhookRequest;
import com.canhlabs.funnyapp.service.payment.WebhookResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SePay bank-transfer adapter (ADR-0037, ADR-0038): HMAC-signed webhook, SePay VietQR image, API v2 feed.
 * Webhook checks in order: 503 (no secret), 403 (IP allowlist), 401 (signature), 400 (payload).
 */
@Component
public class SepayProvider implements PaymentProvider {

    public static final String ID = "sepay";
    static final String SIGNATURE_HEADER = "X-SePay-Signature";
    static final String TIMESTAMP_HEADER = "X-SePay-Timestamp";
    static final String SIGNATURE_PREFIX = "sha256=";
    static final long MAX_SKEW_SECONDS = 300;
    static final String ACK_BODY = "{\"success\": true}";

    private final AppProperties props;
    private final JsonMapper mapper;
    private final SepayTransferFeed feed;
    private final Clock clock;

    @Autowired
    public SepayProvider(AppProperties props, JsonMapper mapper, SepayTransferFeed feed) {
        this(props, mapper, feed, Clock.systemUTC());
    }

    SepayProvider(AppProperties props, JsonMapper mapper, SepayTransferFeed feed, Clock clock) {
        this.props = props;
        this.mapper = mapper;
        this.feed = feed;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "SePay";
    }

    /** {@code {qrBaseUrl}?acc=&bank=&amount=&des=&template=compact}. */
    @Override
    public PaymentInstructions instructions(String orderCode, long amountVnd) {
        AppProperties.Payment payment = props.getPayment();
        String qr = sepay().getQrBaseUrl()
                + "?acc=" + enc(payment.getBankAccount())
                + "&bank=" + enc(payment.getBankCode())
                + "&amount=" + amountVnd
                + "&des=" + enc(orderCode)
                + "&template=compact";
        return new PaymentInstructions(qr, payment.getBankCode(), payment.getBankAccount(), payment.getAccountName());
    }

    @Override
    public WebhookResult receive(WebhookRequest request) {
        if (!sepay().isWebhookConfigured()) {
            return new WebhookResult.Rejected(503, "webhook secret not set");
        }
        // remoteAddr only: X-Forwarded-For is client-controlled; the container resolves it from the trusted proxy
        if (!isIpAllowed(request.remoteAddr())) {
            return new WebhookResult.Rejected(403, "source address not allowed");
        }
        if (!verify(request.header(TIMESTAMP_HEADER), request.header(SIGNATURE_HEADER), request.body())) {
            return new WebhookResult.Rejected(401, "bad, stale or missing signature");
        }
        try {
            return new WebhookResult.Accepted(List.of(parse(request.body())), ACK_BODY);
        } catch (IllegalArgumentException e) {
            return new WebhookResult.Rejected(400, "invalid payload");
        }
    }

    @Override
    public Optional<TransferFeed> feed() {
        return Optional.of(feed);
    }

    boolean isIpAllowed(String remoteAddr) {
        String list = sepay().getAllowedIps();
        if (list == null || list.isBlank()) {
            return true;
        }
        if (remoteAddr == null) {
            return false;
        }
        for (String ip : list.split(",")) {
            if (ip.trim().equals(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when {@code signatureHeader} is {@code sha256=<hex HMAC-SHA256(secret, timestamp + "." + body)>},
     * the timestamp (unix seconds) is within 300 s of now, and the compare is constant-time.
     */
    boolean verify(String timestampHeader, String signatureHeader, byte[] body) {
        if (!sepay().isWebhookConfigured() || timestampHeader == null || signatureHeader == null
                || !signatureHeader.startsWith(SIGNATURE_PREFIX) || body == null) {
            return false;
        }
        String timestamp = timestampHeader.trim();
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - ts) > MAX_SKEW_SECONDS) {
            return false;
        }
        byte[] given = signatureHeader.substring(SIGNATURE_PREFIX.length()).toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] expected = hmacHex(timestamp, body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    /** Throws {@link IllegalArgumentException} when a required field is missing or invalid. */
    BankTransfer parse(byte[] body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("body is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("body is not a JSON object");
        }
        JsonNode id = root.path("id");
        JsonNode amount = root.path("transferAmount");
        String type = root.path("transferType").asText("").trim();
        if (!id.isIntegralNumber() || !id.canConvertToLong() || !amount.isNumber() || type.isEmpty()) {
            throw new IllegalArgumentException("id, transferAmount or transferType missing");
        }
        long amountValue;
        try {
            amountValue = amount.decimalValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("transferAmount must be an integral VND amount");
        }
        return new BankTransfer(ID, Long.toString(id.longValue()), direction(type), amountValue,
                text(root, "code"), text(root, "content"), text(root, "gateway"), text(root, "accountNumber"),
                text(root, "referenceCode"), SepayTime.parse(text(root, "transactionDate")));
    }

    /** SePay sends "in" or "out"; anything but "in" is treated as outgoing, so it is never credited. */
    static TransferDirection direction(String transferType) {
        return "in".equalsIgnoreCase(transferType) ? TransferDirection.IN : TransferDirection.OUT;
    }

    private String hmacHex(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sepay().getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private AppProperties.Sepay sepay() {
        return props.getPayment().getSepay();
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText(null);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
