package com.canhlabs.funnyapp.service.payment.sepay;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import com.canhlabs.funnyapp.service.payment.FeedTransfer;
import com.canhlabs.funnyapp.service.payment.ProviderApiException;
import com.canhlabs.funnyapp.service.payment.TransferFeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SePay user API v2 ({@code GET /v2/transactions}) as a {@link TransferFeed} (ADR-0037 PAY-5, ADR-0038).
 * <ul>
 *   <li>Searched with {@code q}, incoming only, from the start of the window in bank time, oldest first.</li>
 *   <li>Pages until {@code has_more} is false, at most 10 pages, 400 ms apart (SePay allows 3 requests/s).</li>
 *   <li>{@code webhook_success = 1} marks a row the webhook already delivered.</li>
 *   <li>v2 ids are UUIDs and are passed on as they are; outgoing, zero-amount and malformed rows are dropped.</li>
 * </ul>
 */
@Component
public class SepayTransferFeed implements TransferFeed {

    /** Used when SEPAY_API_BASE_URL is blank. */
    public static final String DEFAULT_API_BASE = "https://userapi.sepay.vn";
    public static final String LIST_PATH = "/v2/transactions";
    static final int PER_PAGE = 100;
    public static final int MAX_PAGES = 10;
    static final Duration PAGE_PAUSE = Duration.ofMillis(400);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;
    private final Duration pagePause;
    private final AppProperties props;
    private final JsonMapper mapper;

    @Autowired
    public SepayTransferFeed(AppProperties props, JsonMapper mapper) {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                PAGE_PAUSE, props, mapper);
    }

    /** For tests: a stub HTTP client and no pause between pages. */
    public SepayTransferFeed(HttpClient client, Duration pagePause, AppProperties props, JsonMapper mapper) {
        this.client = client;
        this.pagePause = pagePause;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public boolean isConfigured() {
        return props.getPayment().getSepay().isApiConfigured();
    }

    @Override
    public List<FeedTransfer> fetch(String query, Instant from, Instant to) {
        if (!isConfigured()) {
            throw new ProviderApiException("SEPAY_API_TOKEN is not set", false);
        }
        String token = props.getPayment().getSepay().getApiToken().trim();
        String fromText = SepayTime.format(from);
        List<FeedTransfer> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (page > 1 && !pause()) {
                break;
            }
            JsonNode root = get(token, "?q=" + enc(query) + "&transfer_type=in&transaction_date_from=" + enc(fromText)
                    + "&transaction_date_sort=asc&per_page=" + PER_PAGE + "&page=" + page);
            for (JsonNode n : root.path("data")) {
                FeedTransfer t = toTransfer(n);
                if (t != null && t.transfer().transactionDate().isBefore(to)) {
                    out.add(t);
                }
            }
            if (!root.path("meta").path("pagination").path("has_more").asBoolean(false)) {
                break;
            }
        }
        return out;
    }

    /** Null for an outgoing, zero-amount or malformed row, or one with no readable bank time. */
    static FeedTransfer toTransfer(JsonNode n) {
        String uuid = text(n, "id");
        long amountIn = n.path("amount_in").canConvertToExactIntegral() ? n.path("amount_in").asLong(0) : 0;
        Instant at = SepayTime.parse(text(n, "transaction_date"));
        if (uuid == null || !isUuid(uuid) || amountIn <= 0 || at == null
                || !"in".equalsIgnoreCase(text(n, "transfer_type"))) {
            return null;
        }
        BankTransfer transfer = new BankTransfer(SepayProvider.ID, uuid.trim().toLowerCase(Locale.ROOT), TransferDirection.IN,
                amountIn, text(n, "code"), text(n, "transaction_content"), text(n, "bank_brand_name"),
                text(n, "account_number"), text(n, "reference_number"), at);
        return new FeedTransfer(transfer, n.path("webhook_success").asInt(0) == 1);
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
            throw new ProviderApiException("interrupted", true);
        } catch (IOException | RuntimeException e) {
            // never log the body or the token
            throw new ProviderApiException("transactions API call failed: " + e.getClass().getSimpleName(), true);
        }
        if (response.statusCode() != 200) {
            // 401 bad token, 422 bad query, 429 rate limit
            throw new ProviderApiException("HTTP " + response.statusCode() + " from transactions API", true);
        }
        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw new ProviderApiException("response is not JSON", true);
        }
        if (root == null || !root.path("data").isArray()) {
            throw new ProviderApiException("response has no data array", true);
        }
        return root;
    }

    /** Configured host without trailing slash: sandbox while testing, live in production. */
    String apiBase() {
        String configured = props.getPayment().getSepay().getApiBaseUrl();
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

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
