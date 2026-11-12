package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.config.JsonMappers;
import com.canhlabs.funnyapp.dto.payment.SepayTxPayload;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.event.PaymentEvent;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SepayWebhookServiceImplTest {

    private static final String SECRET = "sepay-test-secret";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String CODE = "FMABCD1234";

    private AppProperties props;
    private PaymentOrderRepository orderRepo;
    private SepayTransactionRepository txRepo;
    private PremiumPlanRepository planRepo;
    private UserRepo userRepo;
    private ApplicationEventPublisher events;
    private SepayWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getSepay().setWebhookSecret(SECRET);
        orderRepo = mock(PaymentOrderRepository.class);
        txRepo = mock(SepayTransactionRepository.class);
        planRepo = mock(PremiumPlanRepository.class);
        userRepo = mock(UserRepo.class);
        events = mock(ApplicationEventPublisher.class);
        service = new SepayWebhookServiceImpl(props, orderRepo, txRepo, JsonMappers.create(),
                new PaymentSettlement(orderRepo, planRepo, userRepo, events), Clock.fixed(NOW, ZoneOffset.UTC));
        when(txRepo.insertIgnore(anyLong(), any(), anyString(), anyString(), anyString(), anyLong(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);
        when(orderRepo.save(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- verify ----

    private static String sign(String secret, String ts, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    private static final String TS = String.valueOf(NOW.getEpochSecond());

    @Test
    void verify_validSignature_passes() throws Exception {
        assertThat(service.verify(TS, sign(SECRET, TS, BODY), BODY)).isTrue();
    }

    @Test
    void verify_edgeOfWindow_passesAndBeyondFails() throws Exception {
        String old = String.valueOf(NOW.getEpochSecond() - 300);
        String tooOld = String.valueOf(NOW.getEpochSecond() - 301);
        String future = String.valueOf(NOW.getEpochSecond() + 301);
        assertThat(service.verify(old, sign(SECRET, old, BODY), BODY)).isTrue();
        assertThat(service.verify(tooOld, sign(SECRET, tooOld, BODY), BODY)).isFalse();
        assertThat(service.verify(future, sign(SECRET, future, BODY), BODY)).isFalse();
    }

    @Test
    void verify_wrongSecretOrTamperedBodyOrTimestamp_fails() throws Exception {
        assertThat(service.verify(TS, sign("other", TS, BODY), BODY)).isFalse();
        assertThat(service.verify(TS, sign(SECRET, TS, BODY), "{\"id\":2}".getBytes(StandardCharsets.UTF_8))).isFalse();
        String shifted = String.valueOf(NOW.getEpochSecond() + 1);
        assertThat(service.verify(shifted, sign(SECRET, TS, BODY), BODY)).isFalse();
    }

    @Test
    void verify_malformedInputs_fail() throws Exception {
        String good = sign(SECRET, TS, BODY);
        assertThat(service.verify(null, good, BODY)).isFalse();
        assertThat(service.verify(TS, null, BODY)).isFalse();
        assertThat(service.verify(TS, good, null)).isFalse();
        assertThat(service.verify("abc", good, BODY)).isFalse();
        assertThat(service.verify(TS, good.substring(7), BODY)).isFalse();
        assertThat(service.verify(TS, "sha256=", BODY)).isFalse();
    }

    @Test
    void verify_uppercaseHexSignature_passes() throws Exception {
        String upper = "sha256=" + sign(SECRET, TS, BODY).substring(7).toUpperCase();
        assertThat(service.verify(TS, upper, BODY)).isTrue();
    }

    @Test
    void verify_noSecret_fails() throws Exception {
        String good = sign(SECRET, TS, BODY);
        props.getSepay().setWebhookSecret("");
        assertThat(service.isConfigured()).isFalse();
        assertThat(service.verify(TS, good, BODY)).isFalse();
    }

    @Test
    void ipAllowlist_offAllowsAll_onAllowsListedOnly() {
        assertThat(service.isIpAllowed("9.9.9.9")).isTrue();
        assertThat(service.isIpAllowed(null)).isTrue();
        props.getSepay().setAllowedIps(" 1.1.1.1 , 2.2.2.2");
        assertThat(service.isIpAllowed("2.2.2.2")).isTrue();
        assertThat(service.isIpAllowed("9.9.9.9")).isFalse();
        assertThat(service.isIpAllowed(null)).isFalse();
    }

    // ---- parse ----

    @Test
    void parse_fullPayload() {
        String json = """
                {"id":92704,"gateway":"MBBank","transactionDate":"2026-11-12 17:00:00","accountNumber":"0123456789",
                 "code":"FMABCD1234","content":"chuyen tien FMABCD1234","transferType":"in","transferAmount":50000,
                 "referenceCode":"REF1","accumulated":1}""";
        SepayTxPayload tx = service.parse(json.getBytes(StandardCharsets.UTF_8));
        assertThat(tx.id()).isEqualTo(92704L);
        assertThat(tx.amount()).isEqualTo(50000L);
        assertThat(tx.transferType()).isEqualTo("in");
        assertThat(tx.gateway()).isEqualTo("MBBank");
        assertThat(tx.code()).isEqualTo(CODE);
        assertThat(tx.referenceCode()).isEqualTo("REF1");
        assertThat(tx.transactionDate()).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
    }

    @Test
    void parse_optionalFieldsMissingOrBadDate_areNull() {
        SepayTxPayload tx = service.parse(
                "{\"id\":5,\"transferType\":\"out\",\"transferAmount\":1,\"code\":null,\"transactionDate\":\"nope\"}"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(tx.code()).isNull();
        assertThat(tx.content()).isNull();
        assertThat(tx.transactionDate()).isNull();
        assertThat(service.parse("{\"id\":5,\"transferType\":\"in\",\"transferAmount\":1}"
                .getBytes(StandardCharsets.UTF_8)).transactionDate()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1]", "null", "{}", "{\"id\":\"x\",\"transferType\":\"in\",\"transferAmount\":1}",
            "{\"id\":1,\"transferAmount\":1}", "{\"id\":1,\"transferType\":\"in\"}",
            "{\"id\":1.5,\"transferType\":\"in\",\"transferAmount\":1}",
            "{\"id\":1,\"transferType\":\"in\",\"transferAmount\":1.5}",
            "{\"id\":1,\"transferType\":\"in\",\"transferAmount\":99999999999999999999}"})
    void parse_invalid_throws(String json) {
        assertThatThrownBy(() -> service.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- process ----

    private static SepayTxPayload tx(long id, long amount, String content) {
        return new SepayTxPayload(id, "MBBank", Instant.parse("2026-11-12T09:59:00Z"), "0123456789012", null,
                content, "in", amount, "REF");
    }

    private static PaymentOrder donation(PaymentOrderStatus status) {
        return PaymentOrder.builder().id(7L).code(CODE).type(PaymentType.DONATION).status(status)
                .expiresAt(NOW.plusSeconds(3600)).build();
    }

    private static PaymentOrder premium(PaymentOrderStatus status, long expected) {
        return PaymentOrder.builder().id(8L).code(CODE).type(PaymentType.PREMIUM).status(status)
                .userId(3L).planId(4L).expectedAmount(expected).expiresAt(NOW.plusSeconds(3600)).build();
    }

    private void withOrder(PaymentOrder order) {
        when(orderRepo.findByCodeForUpdate(CODE)).thenReturn(Optional.of(order));
    }

    private void verifyStored(Long orderId, String matchStatus) {
        verify(txRepo).insertIgnore(anyLong(), eq(orderId), eq(matchStatus), anyString(), anyString(), anyLong(),
                any(), any(), any(), any(), any(), any());
    }

    private PaymentEvent onlyEvent() {
        ArgumentCaptor<PaymentEvent> cap = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).publishEvent(cap.capture());
        return cap.getValue();
    }

    @Test
    void out_isIgnored_noOrderLookup() {
        SepayTxPayload out = new SepayTxPayload(1, "MB", null, null, null, "to " + CODE, "OUT", 5000, null);
        service.process(out, TxSource.WEBHOOK);
        verifyStored(null, "IGNORED");
        verifyNoInteractions(orderRepo, events);
    }

    @Test
    void zeroAmountIn_isIgnored() {
        service.process(tx(1, 0, CODE), TxSource.WEBHOOK);
        verifyStored(null, "IGNORED");
        verifyNoInteractions(orderRepo, events);
    }

    @Test
    void noCode_isUnmatched() {
        service.process(tx(1, 1000, "just a memo"), TxSource.WEBHOOK);
        verifyStored(null, "UNMATCHED");
        verifyNoInteractions(orderRepo, events);
    }

    @Test
    void nullContent_isUnmatched() {
        service.process(tx(1, 1000, null), TxSource.WEBHOOK);
        verifyStored(null, "UNMATCHED");
    }

    @Test
    void unknownCode_isUnmatched() {
        when(orderRepo.findByCodeForUpdate(CODE)).thenReturn(Optional.empty());
        service.process(tx(1, 1000, "pay " + CODE), TxSource.WEBHOOK);
        verifyStored(null, "UNMATCHED");
        verifyNoInteractions(events);
    }

    @Test
    void cancelledOrder_isUnmatched_andNotCredited() {
        PaymentOrder order = donation(PaymentOrderStatus.CANCELLED);
        withOrder(order);
        service.process(tx(1, 1000, CODE), TxSource.WEBHOOK);
        verifyStored(null, "UNMATCHED");
        assertThat(order.getPaidAmount()).isZero();
        verify(orderRepo, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void codeFieldWithPrefix_winsOverContent() {
        withOrder(donation(PaymentOrderStatus.PENDING));
        SepayTxPayload t = new SepayTxPayload(1, null, null, null, " fmabcd1234 ", "other FMZZZZZZZZ", "in", 1000, null);
        service.process(t, TxSource.WEBHOOK);
        verifyStored(7L, "MATCHED");
    }

    @Test
    void codeFieldWithoutPrefix_fallsBackToContent_caseInsensitive() {
        withOrder(donation(PaymentOrderStatus.PENDING));
        SepayTxPayload t = new SepayTxPayload(1, null, null, null, "XX123", "MBVCB.1.fmabcd1234.CT", "in", 1000, null);
        service.process(t, TxSource.WEBHOOK);
        verifyStored(7L, "MATCHED");
    }

    @Test
    void donation_anyAmount_becomesPaid_withEventAndPaidAt() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        service.process(tx(1, 20000, "ung ho " + CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(20000);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(onlyEvent()).isEqualTo(new PaymentEvent(7L, PaymentType.DONATION, PaymentOrderStatus.PAID, 20000));
        verifyNoInteractions(userRepo);
    }

    @Test
    void storedFields_areMaskedAndTruncated() {
        withOrder(donation(PaymentOrderStatus.PENDING));
        String longContent = CODE + "x".repeat(600);
        service.process(new SepayTxPayload(9, "MBBank", null, "0123456789012", null, longContent, "in", 1000, "R"),
                TxSource.RECONCILE);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> account = ArgumentCaptor.forClass(String.class);
        verify(txRepo).insertIgnore(eq(9L), eq(7L), eq("MATCHED"), eq("RECONCILE"), eq("in"), eq(1000L), eq(CODE),
                content.capture(), eq("MBBank"), account.capture(), eq("R"), isNull());
        assertThat(content.getValue()).hasSize(500);
        assertThat(account.getValue()).isEqualTo("****9012");
    }

    @Test
    void maskAccount_shortBlankNull() {
        assertThat(SepayWebhookServiceImpl.maskAccount("123")).isEqualTo("****");
        assertThat(SepayWebhookServiceImpl.maskAccount(" ")).isNull();
        assertThat(SepayWebhookServiceImpl.maskAccount(null)).isNull();
    }

    @Test
    void duplicate_insertReturnsZero_noCredit() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        when(txRepo.insertIgnore(anyLong(), any(), anyString(), anyString(), anyString(), anyLong(), any(), any(),
                any(), any(), any(), any())).thenReturn(0);
        service.process(tx(1, 20000, CODE), TxSource.WEBHOOK);
        assertThat(order.getPaidAmount()).isZero();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verify(orderRepo, never()).save(any());
        verifyNoInteractions(events, userRepo);
    }

    // ---- premium amount table ----

    private PremiumPlan plan30() {
        PremiumPlan plan = PremiumPlan.builder().id(4L).code("PREMIUM_1M").name("1m").durationDays(30)
                .priceVnd(100000).build();
        when(planRepo.findById(4L)).thenReturn(Optional.of(plan));
        return plan;
    }

    private User user(Instant premiumUntil) {
        User u = User.builder().id(3L).premiumUntil(premiumUntil).build();
        when(userRepo.findByIdForUpdate(3L)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void premium_exact_paid_extendsFromNow_whenNull() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        plan30();
        User u = user(null);
        service.process(tx(1, 100000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(u.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 86400));
        verify(userRepo).save(u);
        assertThat(onlyEvent().status()).isEqualTo(PaymentOrderStatus.PAID);
    }

    @Test
    void premium_over_paid_keepsSurplus_andExtendsFromExpired() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        plan30();
        User u = user(NOW.minusSeconds(1000));
        service.process(tx(1, 150000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(150000);
        assertThat(u.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 86400));
    }

    @Test
    void premium_extendsFromFutureExpiry() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        plan30();
        Instant future = NOW.plusSeconds(10L * 86400);
        User u = user(future);
        service.process(tx(1, 100000, CODE), TxSource.WEBHOOK);
        assertThat(u.getPremiumUntil()).isEqualTo(future.plusSeconds(30L * 86400));
    }

    @Test
    void premium_under_isUnderpaid_noEntitlement() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        service.process(tx(1, 40000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
        assertThat(order.getPaidAt()).isNull();
        assertThat(onlyEvent()).isEqualTo(new PaymentEvent(8L, PaymentType.PREMIUM, PaymentOrderStatus.UNDERPAID, 40000));
        verifyNoInteractions(userRepo);
    }

    @Test
    void premium_twoTransfers_accumulateToPaid_eventsOnEachChange() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        plan30();
        User u = user(null);
        service.process(tx(1, 40000, CODE), TxSource.WEBHOOK);
        service.process(tx(2, 60000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(100000);
        assertThat(u.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 86400));
        verify(events, org.mockito.Mockito.times(2)).publishEvent(any(PaymentEvent.class));
    }

    @Test
    void premium_secondUnderTransfer_staysUnderpaid_noSecondEvent() {
        PaymentOrder order = premium(PaymentOrderStatus.UNDERPAID, 100000);
        order.setPaidAmount(40000);
        withOrder(order);
        service.process(tx(2, 10000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
        assertThat(order.getPaidAmount()).isEqualTo(50000);
        verifyNoInteractions(events);
    }

    @Test
    void expiredOrder_stillMatches_andPays() {
        PaymentOrder order = donation(PaymentOrderStatus.EXPIRED);
        withOrder(order);
        service.process(tx(1, 10000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(onlyEvent().status()).isEqualTo(PaymentOrderStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "RESOLVED"})
    void settledOrder_accumulatesSurplus_keepsStatus_noEvent_noSecondGrant(String status) {
        PaymentOrder order = premium(PaymentOrderStatus.valueOf(status), 100000);
        order.setPaidAmount(100000);
        withOrder(order);
        service.process(tx(3, 5000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.valueOf(status));
        assertThat(order.getPaidAmount()).isEqualTo(105000);
        verifyNoInteractions(events, userRepo);
    }

    @Test
    void premium_paidButUserMissing_orderStillPaid_noGrant() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        plan30();
        when(userRepo.findByIdForUpdate(3L)).thenReturn(Optional.empty());
        service.process(tx(1, 100000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(userRepo, never()).save(any());
        verify(events).publishEvent(any(com.canhlabs.funnyapp.event.PaymentEvent.class));
    }

    @Test
    void premium_paidButPlanMissing_orderStillPaid_noGrant() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        withOrder(order);
        when(planRepo.findById(4L)).thenReturn(Optional.empty());
        user(null);
        service.process(tx(1, 100000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(userRepo, never()).save(any());
    }

    @Test
    void premium_noUserNoPlanIds_orderStillPaid() {
        PaymentOrder order = premium(PaymentOrderStatus.PENDING, 100000);
        order.setUserId(null);
        order.setPlanId(null);
        withOrder(order);
        service.process(tx(1, 100000, CODE), TxSource.WEBHOOK);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verifyNoInteractions(planRepo, userRepo);
        verify(events).publishEvent(any(com.canhlabs.funnyapp.event.PaymentEvent.class));
    }

    @Test
    void settlement_amountOverflow_throws() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        order.setPaidAmount(Long.MAX_VALUE);
        withOrder(order);
        assertThatThrownBy(() -> service.process(tx(1, 5, CODE), TxSource.WEBHOOK))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void publicConstructor_usesSystemClock() {
        SepayWebhookServiceImpl s = new SepayWebhookServiceImpl(props, orderRepo, txRepo, JsonMappers.create(),
                new PaymentSettlement(orderRepo, planRepo, userRepo, events));
        assertThat(s.isConfigured()).isTrue();
    }
}
