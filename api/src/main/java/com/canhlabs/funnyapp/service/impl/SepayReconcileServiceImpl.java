package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.entity.SepayTransaction;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.SepayReconcileService;
import com.canhlabs.funnyapp.service.SepayWebhookService;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reconciliation over the SePay user API v2 ({@code GET /v2/transactions}), searched by the order-code prefix.
 * <ul>
 *   <li>Rows with {@code webhook_success = 1} were delivered by the webhook and are skipped.</li>
 *   <li>v2 ids are UUIDs; {@link #storageId} maps each to a stable negative number for {@code sepay_id}
 *       (webhook ids are positive), so a second pass skips it and the two sources never collide.</li>
 *   <li>{@code process} also dedupes on bank reference + amount, so a transfer stored by the webhook is never
 *       credited again under its reconcile id.</li>
 *   <li>Transfers younger than {@link #MIN_AGE} are left to the webhook, which SePay retries for about 33 minutes.</li>
 * </ul>
 */
@Slf4j
@Service
public class SepayReconcileServiceImpl implements SepayReconcileService {

    static final URI API_BASE = URI.create("https://userapi.sepay.vn");
    static final String LIST_PATH = "/v2/transactions";
    /** Covers an outage of up to three days. */
    static final Duration LOOKBACK = Duration.ofHours(72);
    static final Duration MIN_AGE = Duration.ofMinutes(60);
    static final int PER_PAGE = 100;
    static final int MAX_PAGES = 10;
    /** SePay allows 3 requests per second per IP. */
    static final Duration PAGE_PAUSE = Duration.ofMillis(400);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** 2^53 - 1, Number.MAX_SAFE_INTEGER in JavaScript. */
    static final long JS_SAFE_MASK = (1L << 53) - 1;
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter BANK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient client;
    private final URI base;
    private final Clock clock;
    private final Duration pagePause;
    private final AppProperties props;
    private final FeatureFlagService flags;
    private final SepayWebhookService webhookService;
    private final SepayTransactionRepository txRepo;
    private final JsonMapper mapper;

    @Autowired
    public SepayReconcileServiceImpl(AppProperties props, FeatureFlagService flags, SepayWebhookService webhookService,
                                     SepayTransactionRepository txRepo, JsonMapper mapper) {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                API_BASE, Clock.systemUTC(), PAGE_PAUSE, props, flags, webhookService, txRepo, mapper);
    }

    SepayReconcileServiceImpl(HttpClient client, URI base, Clock clock, Duration pagePause, AppProperties props,
                              FeatureFlagService flags, SepayWebhookService webhookService,
                              SepayTransactionRepository txRepo, JsonMapper mapper) {
        this.client = client;
        this.base = base;
        this.clock = clock;
        this.pagePause = pagePause;
        this.props = props;
        this.flags = flags;
        this.webhookService = webhookService;
        this.txRepo = txRepo;
        this.mapper = mapper;
    }

    @Override
    public int reconcile() {
        if (!flags.isEnabled(AppConstant.Flags.SEPAY_RECONCILE, false)) {
            log.debug("SePay reconcile skipped: flag off");
            return 0;
        }
        String token = props.getSepay().getApiToken();
        if (token == null || token.isBlank()) {
            log.warn("SePay reconcile skipped: SEPAY_API_TOKEN is not set");
            return 0;
        }
        Instant settledBefore = clock.instant().minus(MIN_AGE);
        List<SepayTxPayload> missed = fetchMissed(token.trim()).stream()
                .filter(t -> t.transactionDate() != null && t.transactionDate().isBefore(settledBefore))
                .toList();
        if (missed.isEmpty()) {
            return 0;
        }
        Set<Long> known = txRepo.findAllById(missed.stream().map(SepayTxPayload::id).toList()).stream()
                .map(SepayTransaction::getSepayId).collect(Collectors.toSet());
        int recovered = 0;
        for (SepayTxPayload tx : missed) {
            if (known.contains(tx.id())) {
                continue;
            }
            try {
                webhookService.process(tx, TxSource.RECONCILE);
                recovered++;
            } catch (RuntimeException e) {
                log.warn("SePay reconcile could not process tx {}: {}", tx.id(), e.getClass().getSimpleName());
            }
        }
        log.info("SePay reconcile: {} candidate(s) without a delivered webhook, {} processed", missed.size(), recovered);
        return recovered;
    }

    /**
     * Incoming transfers in the look-back window that carry the code prefix and whose webhook was not delivered.
     * Any API failure ends the pass with what was read so far; the next run reads the window again.
     */
    List<SepayTxPayload> fetchMissed(String token) {
        String prefix = props.getSepay().getCodePrefix().trim().toUpperCase(Locale.ROOT);
        String from = LocalDateTime.ofInstant(clock.instant().minus(LOOKBACK), BANK_ZONE).format(BANK_TIME);
        List<SepayTxPayload> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (page > 1 && !pause()) {
                break;
            }
            JsonNode root = get(token, "?q=" + enc(prefix) + "&transfer_type=in&transaction_date_from=" + enc(from)
                    + "&transaction_date_sort=asc&per_page=" + PER_PAGE + "&page=" + page);
            if (root == null) {
                break;
            }
            for (JsonNode n : root.path("data")) {
                SepayTxPayload tx = toPayload(n, prefix);
                if (tx != null) {
                    out.add(tx);
                }
            }
            if (!root.path("meta").path("pagination").path("has_more").asBoolean(false)) {
                break;
            }
        }
        return out;
    }

    private JsonNode get(String token, String query) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.toString().replaceAll("/+$", "") + LIST_PATH + query))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 401 bad token, 422 bad query, 429 rate limit: stop, the next run retries
                log.warn("SePay reconcile: HTTP {} from transactions API", response.statusCode());
                return null;
            }
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.path("data").isArray()) {
                log.warn("SePay reconcile: response has no data array");
                return null;
            }
            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            // never log the body or the token
            log.warn("SePay reconcile: transactions API call failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(pagePause);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Null for a delivered webhook, an outgoing or malformed row, or one without our code prefix. */
    private SepayTxPayload toPayload(JsonNode n, String prefix) {
        if (n.path("webhook_success").asInt(0) == 1) {
            return null;
        }
        String uuid = text(n, "id");
        long amountIn = n.path("amount_in").canConvertToExactIntegral() ? n.path("amount_in").asLong(0) : 0;
        if (uuid == null || amountIn <= 0 || !"in".equalsIgnoreCase(text(n, "transfer_type"))) {
            return null;
        }
        String code = text(n, "code");
        String content = text(n, "transaction_content");
        // q also matches the prefix inside unrelated words; keep rows whose code or memo really carries it
        if (!startsWithPrefix(code, prefix) && (content == null || !content.toUpperCase(Locale.ROOT).contains(prefix))) {
            return null;
        }
        String account = props.getSepay().getBankAccount();
        String txAccount = text(n, "account_number");
        if (account != null && !account.isBlank() && txAccount != null && !account.trim().equals(txAccount.trim())) {
            return null;
        }
        Long id = storageId(uuid);
        if (id == null) {
            return null;
        }
        return new SepayTxPayload(id, text(n, "bank_brand_name"),
                SepayWebhookServiceImpl.bankTime(text(n, "transaction_date")), txAccount, code, content, "in",
                amountIn, text(n, "reference_number"));
    }

    /**
     * Stable negative id for a v2 UUID; webhook ids are positive, so the two never collide. Kept within 53 bits
     * because the admin page reads it as a JavaScript number and sends it back to link or ignore the transfer.
     * Null if not a UUID.
     */
    static Long storageId(String uuid) {
        UUID u;
        try {
            u = UUID.fromString(uuid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            // hash first so every UUID bit counts before the cut to 53 bits
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(u.toString().getBytes(StandardCharsets.US_ASCII));
            long v = ByteBuffer.wrap(h).getLong() & JS_SAFE_MASK;
            return v == 0 ? -1L : -v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean startsWithPrefix(String code, String prefix) {
        return code != null && code.trim().toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
