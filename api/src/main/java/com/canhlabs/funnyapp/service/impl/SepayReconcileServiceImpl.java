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
import java.util.function.Predicate;
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

    /** Used when SEPAY_API_BASE_URL is blank. */
    static final String DEFAULT_API_BASE = "https://userapi.sepay.vn";
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
                Clock.systemUTC(), PAGE_PAUSE, props, flags, webhookService, txRepo, mapper);
    }

    SepayReconcileServiceImpl(HttpClient client, Clock clock, Duration pagePause, AppProperties props,
                              FeatureFlagService flags, SepayWebhookService webhookService,
                              SepayTransactionRepository txRepo, JsonMapper mapper) {
        this.client = client;
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
        String prefix = prefix();
        String from = bankTime(clock.instant().minus(LOOKBACK));
        List<SepayTxPayload> found = new ArrayList<>();
        try {
            fetch(token.trim(), prefix, from, n -> carriesPrefix(n, prefix), found);
        } catch (SepayApiException e) {
            // keep what earlier pages gave; the next run reads the whole window again
            log.warn("SePay reconcile: {}", e.getMessage());
        }
        Instant settledBefore = clock.instant().minus(MIN_AGE);
        List<SepayTxPayload> missed = found.stream()
                .filter(t -> t.transactionDate() != null && t.transactionDate().isBefore(settledBefore))
                .toList();
        int recovered = processNew(missed);
        // Grafana "Business Stats" (Job Health row) parses this line: keep its text and INFO level
        log.info("SePay reconcile run: undelivered={}, settled={}, processed={}", found.size(), missed.size(), recovered);
        return recovered;
    }

    @Override
    public CheckResult checkCode(String code, Instant since) {
        String token = props.getSepay().getApiToken();
        if (token == null || token.isBlank()) {
            throw new SepayApiException("SEPAY_API_TOKEN is not set", false);
        }
        String wanted = code.trim().toUpperCase(Locale.ROOT);
        // An hour of slack before the order: bank and server clocks differ, and the query filters on bank time
        String from = bankTime(since.minus(Duration.ofHours(1)));
        List<SepayTxPayload> found = new ArrayList<>();
        fetch(token.trim(), wanted, from, n -> carriesCode(n, wanted), found);
        int processed = processNew(found);
        log.info("SePay check for order {}: {} undelivered transfer(s), {} processed", wanted, found.size(), processed);
        return new CheckResult(found.size(), processed);
    }

    /** Sends each transfer not stored yet through the webhook path; one failure does not stop the rest. */
    private int processNew(List<SepayTxPayload> txs) {
        if (txs.isEmpty()) {
            return 0;
        }
        Set<Long> known = txRepo.findAllById(txs.stream().map(SepayTxPayload::id).toList()).stream()
                .map(SepayTransaction::getSepayId).collect(Collectors.toSet());
        int processed = 0;
        for (SepayTxPayload tx : txs) {
            if (known.contains(tx.id())) {
                continue;
            }
            try {
                // process() also skips a transfer stored under another id (same bank reference + amount)
                webhookService.process(tx, TxSource.RECONCILE);
                processed++;
            } catch (RuntimeException e) {
                log.warn("SePay reconcile could not process tx {}: {}", tx.id(), e.getClass().getSimpleName());
            }
        }
        return processed;
    }

    /**
     * Incoming transfers matching {@code q} since {@code from} (bank time) whose webhook was not delivered and
     * that pass {@code keep}. Pages until has_more is false; a failed page throws, keeping earlier pages in out.
     */
    void fetch(String token, String q, String from, Predicate<JsonNode> keep, List<SepayTxPayload> out) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (page > 1 && !pause()) {
                return;
            }
            JsonNode root = get(token, "?q=" + enc(q) + "&transfer_type=in&transaction_date_from=" + enc(from)
                    + "&transaction_date_sort=asc&per_page=" + PER_PAGE + "&page=" + page);
            for (JsonNode n : root.path("data")) {
                SepayTxPayload tx = keep.test(n) ? toPayload(n) : null;
                if (tx != null) {
                    out.add(tx);
                }
            }
            if (!root.path("meta").path("pagination").path("has_more").asBoolean(false)) {
                return;
            }
        }
    }

    private JsonNode get(String token, String query) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + LIST_PATH + query))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SepayApiException("interrupted", true);
        } catch (IOException | RuntimeException e) {
            // never log the body or the token
            throw new SepayApiException("transactions API call failed: " + e.getClass().getSimpleName(), true);
        }
        if (response.statusCode() != 200) {
            // 401 bad token, 422 bad query, 429 rate limit
            throw new SepayApiException("HTTP " + response.statusCode() + " from transactions API", true);
        }
        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw new SepayApiException("response is not JSON", true);
        }
        if (root == null || !root.path("data").isArray()) {
            throw new SepayApiException("response has no data array", true);
        }
        return root;
    }

    private String prefix() {
        return props.getSepay().getCodePrefix().trim().toUpperCase(Locale.ROOT);
    }

    private static String bankTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, BANK_ZONE).format(BANK_TIME);
    }

    /** Configured host without trailing slash: sandbox while testing, live in production. */
    String apiBase() {
        String configured = props.getSepay().getApiBaseUrl();
        String base = configured == null || configured.isBlank() ? DEFAULT_API_BASE : configured.trim();
        return base.replaceAll("/+$", "");
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

    /** q also matches inside unrelated words; keep rows whose code or memo really carries the prefix. */
    private static boolean carriesPrefix(JsonNode n, String prefix) {
        return startsWith(text(n, "code"), prefix) || contains(text(n, "transaction_content"), prefix);
    }

    /** One order: SePay's extracted code is that order's code, or the memo contains it. */
    private static boolean carriesCode(JsonNode n, String code) {
        String c = text(n, "code");
        return (c != null && c.trim().equalsIgnoreCase(code)) || contains(text(n, "transaction_content"), code);
    }

    /**
     * Null for a delivered webhook (already stored by the webhook path), an outgoing or malformed row. The
     * account is not checked, like the webhook: the API key only sees this company's accounts, and the order code
     * still has to match (sandbox test accounts differ from the live one).
     */
    private static SepayTxPayload toPayload(JsonNode n) {
        if (n.path("webhook_success").asInt(0) == 1) {
            return null;
        }
        String uuid = text(n, "id");
        long amountIn = n.path("amount_in").canConvertToExactIntegral() ? n.path("amount_in").asLong(0) : 0;
        if (uuid == null || amountIn <= 0 || !"in".equalsIgnoreCase(text(n, "transfer_type"))) {
            return null;
        }
        Long id = storageId(uuid);
        if (id == null) {
            return null;
        }
        return new SepayTxPayload(id, text(n, "bank_brand_name"),
                SepayWebhookServiceImpl.bankTime(text(n, "transaction_date")), text(n, "account_number"),
                text(n, "code"), text(n, "transaction_content"), "in", amountIn, text(n, "reference_number"));
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

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.trim().toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    private static boolean contains(String value, String part) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(part);
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
