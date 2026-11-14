package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.config.JsonMappers;
import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.entity.SepayTransaction;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.service.SepayReconcileService;
import com.canhlabs.funnyapp.service.SepayWebhookService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SepayReconcileServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z"); // 15:00 in Vietnam
    private static final String TOKEN = "FAKE_SEPAY_TOKEN";
    private static final String SETTLED = "2026-11-14 13:30:00"; // 90 minutes before NOW
    private static final String U1 = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String U2 = "b1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final List<String> queries = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger status = new AtomicInteger(200);
    /** Response body per page number; a missing page answers with an empty last page. */
    private final Map<Integer, String> pages = new ConcurrentHashMap<>();
    private HttpServer server;
    private AppProperties props;
    private boolean flagOn;
    private SepayWebhookService webhookService;
    private SepayTransactionRepository txRepo;
    private SepayReconcileServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(SepayReconcileServiceImpl.LIST_PATH, ex -> {
            requests.incrementAndGet();
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            String q = URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            queries.add(q);
            int page = Integer.parseInt(q.replaceAll(".*[?&]?page=(\\d+).*", "$1"));
            String body = pages.getOrDefault(page, page(false));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                ex.getResponseBody().write(out);
            }
            ex.close();
        });
        server.start();
        props = new AppProperties();
        props.getSepay().setApiToken(TOKEN);
        props.getSepay().setBankAccount("1017588888");
        flagOn = true;
        webhookService = mock(SepayWebhookService.class);
        txRepo = mock(SepayTransactionRepository.class);
        when(txRepo.findAllById(anyList())).thenReturn(List.of());
        props.getSepay().setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        service = new SepayReconcileServiceImpl(HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO, props, (flag, def) -> AppConstant.Flags.SEPAY_RECONCILE.equals(flag) && flagOn,
                webhookService, txRepo, JsonMappers.create());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String tx(String id, long amountIn, String code, String content, String date, Integer webhookOk) {
        return """
                {"id":"%s","transaction_date":"%s","account_number":"1017588888","va":null,"transfer_type":"in",
                 "amount_in":%d,"amount_out":0,"accumulated":0,"transaction_content":"%s","reference_number":"FT1",
                 "code":%s,"bank_brand_name":"Vietcombank","bank_account_id":"f9e8d7c6-b5a4-3210-fedc-ba0987654321",
                 "va_id":null,"webhook_success":%s}
                """.formatted(id, date, amountIn, content, code == null ? "null" : "\"" + code + "\"",
                webhookOk == null ? "null" : webhookOk);
    }

    private static String tx(String id, long amountIn, String content) {
        return tx(id, amountIn, null, content, SETTLED, 0);
    }

    private static String page(boolean hasMore, String... txs) {
        return "{\"status\":\"success\",\"data\":[" + String.join(",", txs)
                + "],\"meta\":{\"pagination\":{\"per_page\":100,\"has_more\":" + hasMore + "}}}";
    }

    private List<SepayTxPayload> processed(int times) {
        ArgumentCaptor<SepayTxPayload> captor = ArgumentCaptor.forClass(SepayTxPayload.class);
        verify(webhookService, times(times)).process(captor.capture(), eq(TxSource.RECONCILE));
        return captor.getAllValues();
    }

    @Test
    void flagOff_doesNotCallSepay() {
        flagOn = false;

        assertThat(service.reconcile()).isZero();

        assertThat(requests.get()).isZero();
        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void blankToken_doesNotCallSepay() {
        props.getSepay().setApiToken(" ");

        assertThat(service.reconcile()).isZero();

        assertThat(requests.get()).isZero();
    }

    @Test
    void missedTransfer_searchedByCodePrefix_goesThroughWebhookPath() {
        pages.put(1, page(false, tx(U1, 50000, "FMLVKXU9WH", "FMLVKXU9WH ung ho", SETTLED, 0)));

        assertThat(service.reconcile()).isEqualTo(1);

        assertThat(auth.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(queries.get(0)).contains("q=FM").contains("transfer_type=in")
                .contains("transaction_date_from=2026-11-11 15:00:00").contains("transaction_date_sort=asc")
                .contains("per_page=100").contains("page=1");
        SepayTxPayload p = processed(1).get(0);
        assertThat(p.id()).isEqualTo(SepayReconcileServiceImpl.storageId(U1)).isNegative();
        assertThat(p.amount()).isEqualTo(50000L);
        assertThat(p.transferType()).isEqualTo("in");
        assertThat(p.code()).isEqualTo("FMLVKXU9WH");
        assertThat(p.referenceCode()).isEqualTo("FT1");
        assertThat(p.gateway()).isEqualTo("Vietcombank");
        assertThat(p.transactionDate()).isEqualTo(Instant.parse("2026-11-14T06:30:00Z"));
    }

    @Test
    void deliveredWebhook_isSkipped_butUnknownDeliveryIsProcessed() {
        pages.put(1, page(false, tx(U1, 50000, null, "FMAAAAAAAA", SETTLED, 1),
                tx(U2, 20000, null, "FMBBBBBBBB", SETTLED, null)));

        assertThat(service.reconcile()).isEqualTo(1);

        assertThat(processed(1).get(0).content()).isEqualTo("FMBBBBBBBB");
    }

    @Test
    void alreadyStoredByAnEarlierPass_isSkipped() {
        pages.put(1, page(false, tx(U1, 50000, "FMAAAAAAAA"), tx(U2, 20000, "FMBBBBBBBB")));
        when(txRepo.findAllById(anyList())).thenReturn(
                List.of(SepayTransaction.builder().sepayId(SepayReconcileServiceImpl.storageId(U1)).build()));

        assertThat(service.reconcile()).isEqualTo(1);

        assertThat(processed(1).get(0).content()).isEqualTo("FMBBBBBBBB");
    }

    @Test
    void rowsWithoutOurPrefix_outgoing_malformed_orTooRecent_areDropped_anyAccountIsKept() {
        // sandbox test accounts differ from the live one; the order code is what has to match
        String otherAccount = tx(U2, 10000, "other account FMCCCCCCCC").replace("1017588888", "3571248070");
        pages.put(1, page(false,
                tx(U1, 10000, "transfer from mom"),                         // no FM anywhere
                tx("not-a-uuid", 10000, "FMDDDDDDDD"),
                tx("c1b2c3d4-e5f6-7890-abcd-ef1234567890", 0, "FMEEEEEEEE"),
                tx("d1b2c3d4-e5f6-7890-abcd-ef1234567890", 10000, null, "FMFFFFFFFF", "2026-11-14 14:30:00", 0),
                tx("e1b2c3d4-e5f6-7890-abcd-ef1234567890", 10000, null, "FMGGGGGGGG", "", 0),
                otherAccount,
                tx("f1b2c3d4-e5f6-7890-abcd-ef1234567890", 10000, "ok fmhhhhhhhh")));

        assertThat(service.reconcile()).isEqualTo(2);

        assertThat(processed(2)).extracting(SepayTxPayload::content)
                .containsExactly("other account FMCCCCCCCC", "ok fmhhhhhhhh");
    }

    @Test
    void readsPagesUntilHasMoreIsFalse() {
        pages.put(1, page(true, tx(U1, 10000, "FMAAAAAAAA")));
        pages.put(2, page(false, tx(U2, 20000, "FMBBBBBBBB")));

        assertThat(service.reconcile()).isEqualTo(2);

        assertThat(requests.get()).isEqualTo(2);
        assertThat(queries.get(1)).contains("page=2");
    }

    @Test
    void stopsAtMaxPages() {
        for (int i = 1; i <= SepayReconcileServiceImpl.MAX_PAGES + 2; i++) {
            pages.put(i, page(true));
        }

        service.reconcile();

        assertThat(requests.get()).isEqualTo(SepayReconcileServiceImpl.MAX_PAGES);
    }

    @Test
    void oneFailingTransfer_doesNotStopTheRest() {
        pages.put(1, page(false, tx(U1, 10000, "FMAAAAAAAA"), tx(U2, 20000, "FMBBBBBBBB")));
        long failing = SepayReconcileServiceImpl.storageId(U1);
        doThrow(new IllegalStateException("db")).when(webhookService)
                .process(argThat(t -> t != null && t.id() == failing), any());

        assertThat(service.reconcile()).isEqualTo(1);

        verify(webhookService, times(2)).process(any(), any());
    }

    @Test
    void apiErrors_processNothing() {
        status.set(429);
        pages.put(1, "{\"status\":\"error\",\"error_code\":\"rate_limited\"}");
        assertThat(service.reconcile()).isZero();

        status.set(200);
        pages.put(1, "not json");
        assertThat(service.reconcile()).isZero();

        pages.put(1, "{\"status\":\"success\",\"data\":null}");
        assertThat(service.reconcile()).isZero();

        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void unreachableApi_processesNothing() {
        server.stop(0);

        assertThat(service.reconcile()).isZero();

        verify(webhookService, never()).process(any(), any());
    }

    // ---- checkCode (admin "Check on SePay") ----

    @Test
    void checkCode_searchesTheExactCode_ignoresFlagAndAge_processesOnlyThatOrdersTransfers() {
        flagOn = false; // the admin action does not depend on the job flag
        pages.put(1, page(false,
                tx(U1, 50000, "FMAKN7GW7U", "FMAKN7GW7U", "2026-11-14 14:59:00", 0), // 1 min old: still taken
                tx(U2, 20000, null, "fmakn7gw7u ung ho", SETTLED, null),
                tx("c1b2c3d4-e5f6-7890-abcd-ef1234567890", 10000, "FMOTHER001", "FMOTHER001", SETTLED, 0),
                tx("d1b2c3d4-e5f6-7890-abcd-ef1234567890", 10000, "FMAKN7GW7U", "FMAKN7GW7U", SETTLED, 1)));

        SepayReconcileService.CheckResult r = service.checkCode(" fmakn7gw7u ", Instant.parse("2026-11-13T16:02:00Z"));

        assertThat(r.found()).isEqualTo(2);
        assertThat(r.processed()).isEqualTo(2);
        assertThat(queries.get(0)).contains("q=FMAKN7GW7U").contains("transaction_date_from=2026-11-13 22:02:00");
        assertThat(processed(2)).extracting(SepayTxPayload::amount).containsExactly(50000L, 20000L);
    }

    @Test
    void checkCode_alreadyStored_isFoundButNotProcessedAgain() {
        pages.put(1, page(false, tx(U1, 50000, "FMAKN7GW7U", "FMAKN7GW7U", SETTLED, 0)));
        when(txRepo.findAllById(anyList())).thenReturn(
                List.of(SepayTransaction.builder().sepayId(SepayReconcileServiceImpl.storageId(U1)).build()));

        SepayReconcileService.CheckResult r = service.checkCode("FMAKN7GW7U", NOW);

        assertThat(r.found()).isEqualTo(1);
        assertThat(r.processed()).isZero();
        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void checkCode_failures_throwWithConfiguredFlag() {
        props.getSepay().setApiToken("");
        assertThatThrownBy(() -> service.checkCode("FMAKN7GW7U", NOW))
                .isInstanceOfSatisfying(SepayReconcileService.SepayApiException.class,
                        e -> assertThat(e.isConfigured()).isFalse());
        assertThat(requests.get()).isZero();

        props.getSepay().setApiToken(TOKEN);
        status.set(401);
        pages.put(1, "{\"status\":\"error\",\"error_code\":\"unauthorized\"}");
        assertThatThrownBy(() -> service.checkCode("FMAKN7GW7U", NOW))
                .isInstanceOfSatisfying(SepayReconcileService.SepayApiException.class,
                        e -> assertThat(e.isConfigured()).isTrue());
        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void apiBase_usesConfiguredHost_elseLive() {
        props.getSepay().setApiBaseUrl(" https://userapi-sandbox.sepay.vn// ");
        assertThat(service.apiBase()).isEqualTo("https://userapi-sandbox.sepay.vn");

        props.getSepay().setApiBaseUrl(" ");
        assertThat(service.apiBase()).isEqualTo("https://userapi.sepay.vn");

        props.getSepay().setApiBaseUrl(null);
        assertThat(service.apiBase()).isEqualTo(SepayReconcileServiceImpl.DEFAULT_API_BASE);
    }

    @Test
    void storageId_isStableNegativeAndRejectsNonUuid() {
        assertThat(SepayReconcileServiceImpl.storageId(U1)).isEqualTo(SepayReconcileServiceImpl.storageId(" " + U1))
                .isNegative().isNotEqualTo(SepayReconcileServiceImpl.storageId(U2))
                .isGreaterThanOrEqualTo(-SepayReconcileServiceImpl.JS_SAFE_MASK); // exact as a JavaScript number
        assertThat(SepayReconcileServiceImpl.storageId("49700")).isNull();
    }
}
