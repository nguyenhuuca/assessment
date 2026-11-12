package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.service.SepayWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HMAC-verified SePay webhook and the shared transfer-matching path (ADR-0037). */
@Slf4j
@Service
public class SepayWebhookServiceImpl implements SepayWebhookService {

    static final String SIGNATURE_PREFIX = "sha256=";
    static final long MAX_SKEW_SECONDS = 300;
    static final int MAX_CONTENT = 500;
    static final int MAX_CODE = 64;
    static final int MAX_FIELD = 64;
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter BANK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppProperties props;
    private final PaymentOrderRepository orderRepo;
    private final SepayTransactionRepository txRepo;
    private final JsonMapper mapper;
    private final PaymentSettlement settlement;
    private final Clock clock;

    @Autowired
    public SepayWebhookServiceImpl(AppProperties props, PaymentOrderRepository orderRepo,
                                   SepayTransactionRepository txRepo,
                                   JsonMapper mapper, PaymentSettlement settlement) {
        this(props, orderRepo, txRepo, mapper, settlement, Clock.systemUTC());
    }

    SepayWebhookServiceImpl(AppProperties props, PaymentOrderRepository orderRepo,
                            SepayTransactionRepository txRepo,
                            JsonMapper mapper, PaymentSettlement settlement, Clock clock) {
        this.props = props;
        this.orderRepo = orderRepo;
        this.txRepo = txRepo;
        this.mapper = mapper;
        this.settlement = settlement;
        this.clock = clock;
    }

    @Override
    public boolean isConfigured() {
        return props.getSepay().isWebhookConfigured();
    }

    @Override
    public boolean isIpAllowed(String remoteAddr) {
        String list = props.getSepay().getAllowedIps();
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

    @Override
    public boolean verify(String timestampHeader, String signatureHeader, byte[] body) {
        if (!isConfigured() || timestampHeader == null || signatureHeader == null
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
        byte[] given = signatureHeader.substring(SIGNATURE_PREFIX.length()).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = hmacHex(timestamp, body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    @Override
    public SepayTxPayload parse(byte[] body) {
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
        return new SepayTxPayload(id.longValue(), text(root, "gateway"), bankTime(text(root, "transactionDate")),
                text(root, "accountNumber"), text(root, "code"), text(root, "content"), type,
                amount.longValue(), text(root, "referenceCode"));
    }

    @Override
    @Transactional
    public void process(SepayTxPayload tx, TxSource source) {
        String code = extractCode(tx);
        if ("out".equalsIgnoreCase(tx.transferType()) || tx.amount() <= 0) {
            store(tx, source, null, TxMatchStatus.IGNORED, code);
            log.info("SePay tx {} ignored (source={})", tx.id(), source);
            return;
        }
        Optional<PaymentOrder> found = code == null ? Optional.empty() : orderRepo.findByCodeForUpdate(code);
        if (found.isEmpty() || found.get().getStatus() == PaymentOrderStatus.CANCELLED) {
            store(tx, source, null, TxMatchStatus.UNMATCHED, code);
            log.info("SePay tx {} unmatched (source={})", tx.id(), source);
            return;
        }
        PaymentOrder order = found.get();
        if (store(tx, source, order.getId(), TxMatchStatus.MATCHED, code) == 0) {
            log.info("SePay tx {} already processed (source={})", tx.id(), source);
            return;
        }
        settlement.apply(order, tx.id(), tx.amount(), Instant.now(clock));
    }

    private int store(SepayTxPayload tx, TxSource source, Long orderId, TxMatchStatus status, String code) {
        return txRepo.insertIgnore(tx.id(), orderId, status.name(), source.name(),
                cut(tx.transferType().toLowerCase(Locale.ROOT), 8), tx.amount(), cut(code, MAX_CODE),
                cut(tx.content(), MAX_CONTENT), cut(tx.gateway(), MAX_FIELD), maskAccount(tx.accountNumber()),
                cut(tx.referenceCode(), MAX_FIELD), tx.transactionDate());
    }

    /** The payload's own code field when it carries our prefix, else the first prefix+8 token in the memo. */
    String extractCode(SepayTxPayload tx) {
        String prefix = props.getSepay().getCodePrefix().toUpperCase(Locale.ROOT);
        if (tx.code() != null) {
            String c = tx.code().trim().toUpperCase(Locale.ROOT);
            if (c.startsWith(prefix)) {
                return c;
            }
        }
        if (tx.content() == null) {
            return null;
        }
        Matcher m = Pattern.compile(Pattern.quote(prefix) + "[A-Z0-9]{8}").matcher(tx.content().toUpperCase(Locale.ROOT));
        return m.find() ? m.group() : null;
    }

    static String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String a = account.trim();
        return a.length() <= 4 ? "****" : "****" + a.substring(a.length() - 4);
    }

    private String hmacHex(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getSepay().getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText(null);
    }

    private static Instant bankTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), BANK_TIME).atZone(BANK_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
