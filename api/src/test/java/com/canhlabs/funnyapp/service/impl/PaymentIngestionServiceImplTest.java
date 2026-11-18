package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.TransferDirection;
import com.canhlabs.funnyapp.enums.TxSource;
import com.canhlabs.funnyapp.event.PaymentEvent;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.repo.PaymentTransactionRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.payment.BankTransfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentIngestionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String CODE = "FMABCD1234";

    private AppProperties props;
    private PaymentOrderRepository orderRepo;
    private PaymentTransactionRepository txRepo;
    private PremiumPlanRepository planRepo;
    private UserRepo userRepo;
    private ApplicationEventPublisher events;
    private PaymentIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        orderRepo = mock(PaymentOrderRepository.class);
        txRepo = mock(PaymentTransactionRepository.class);
        planRepo = mock(PremiumPlanRepository.class);
        userRepo = mock(UserRepo.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PaymentIngestionServiceImpl(props, orderRepo, txRepo,
                new PaymentSettlement(orderRepo, planRepo, userRepo, events), Clock.fixed(NOW, ZoneOffset.UTC));
        when(txRepo.insertIgnore(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(), any(),
                any(), any(), any(), any(), any())).thenReturn(1);
        when(orderRepo.save(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- process ----

    private static BankTransfer tx(long id, long amount, String content) {
        return payload(id, "MBBank", Instant.parse("2026-11-12T09:59:00Z"), "0123456789012", null,
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
        verify(txRepo).insertIgnore(anyString(), anyString(), eq(orderId), eq(matchStatus), anyString(), anyString(), anyLong(),
                any(), any(), any(), any(), any(), any());
    }

    private PaymentEvent onlyEvent() {
        ArgumentCaptor<PaymentEvent> cap = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).publishEvent(cap.capture());
        return cap.getValue();
    }

    @Test
    void sameReferenceUnderAnotherId_isSkipped_noDoubleCredit() {
        when(txRepo.existsSameTransferUnderOtherId("REF", 50000L, "sepay", "2")).thenReturn(true);

        service.process(tx(2, 50000, CODE), TxSource.RECONCILE);

        verify(txRepo, never()).insertIgnore(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(),
                any(), any(), any(), any(), any(), any());
        verifyNoInteractions(orderRepo, events);
    }

    private void verifyNotStored() {
        verify(txRepo, never()).insertIgnore(anyString(), anyString(), any(), anyString(), anyString(), anyString(),
                anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void matchedTransfer_rechecksReferenceAfterOrderLock_andSkipsWhenStoredMeanwhile() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        when(txRepo.existsSameTransferUnderOtherId("REF", 50000L, "sepay", "2")).thenReturn(false, true);

        service.process(tx(2, 50000, CODE), TxSource.WEBHOOK);

        InOrder inOrder = inOrder(txRepo, orderRepo);
        inOrder.verify(txRepo).existsSameTransferUnderOtherId("REF", 50000L, "sepay", "2");
        inOrder.verify(orderRepo).findByCodeForUpdate(CODE);
        inOrder.verify(txRepo).existsSameTransferUnderOtherId("REF", 50000L, "sepay", "2");
        verifyNotStored();
        assertThat(order.getPaidAmount()).isZero();
        verifyNoInteractions(events);
    }

    @Test
    void blankReference_sameOrderAmountTimestampUnderOtherId_isSkipped() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        Instant at = Instant.parse("2026-11-12T09:59:00Z");
        when(txRepo.existsSameTransferForOrderUnderOtherId(7L, 50000L, at, "sepay", "2")).thenReturn(true);

        service.process(payload(2, "MB", at, null, null, CODE, "in", 50000, " "), TxSource.WEBHOOK);

        verifyNotStored();
        assertThat(order.getPaidAmount()).isZero();
        verifyNoInteractions(events);
    }

    @Test
    void blankReference_differentTimestampOrAmount_isStillCredited() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        Instant at = Instant.parse("2026-11-12T09:59:00Z");
        when(txRepo.existsSameTransferForOrderUnderOtherId(7L, 50000L, at, "sepay", "2")).thenReturn(true);

        service.process(payload(3, "MB", at.plusSeconds(60), null, null, CODE, "in", 50000, null), TxSource.WEBHOOK);

        verifyStored(7L, "MATCHED");
        assertThat(order.getPaidAmount()).isEqualTo(50000);
    }

    @Test
    void blankReference_nullTimestamp_isCredited_withoutFallbackQuery() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);

        service.process(payload(4, "MB", null, null, null, CODE, "in", 50000, null), TxSource.WEBHOOK);

        verify(txRepo, never()).existsSameTransferForOrderUnderOtherId(anyLong(), anyLong(), any(), any(), any());
        verifyStored(7L, "MATCHED");
    }

    @Test
    void reference_present_skipsTheBlankReferenceFallback() {
        withOrder(donation(PaymentOrderStatus.PENDING));

        service.process(tx(5, 1000, CODE), TxSource.WEBHOOK);

        verify(txRepo, never()).existsSameTransferForOrderUnderOtherId(anyLong(), anyLong(), any(), any(), any());
        verifyStored(7L, "MATCHED");
    }

    @Test
    void blankReference_skipsTheReferenceCheck() {
        service.process(payload(1, "MB", null, null, null, "no code", "in", 1000, " "), TxSource.WEBHOOK);

        verify(txRepo, never()).existsSameTransferUnderOtherId(any(), anyLong(), any(), any());
        verifyStored(null, "UNMATCHED");
    }

    @Test
    void out_isIgnored_noOrderLookup() {
        BankTransfer out = payload(1, "MB", null, null, null, "to " + CODE, "OUT", 5000, null);
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
        BankTransfer t = payload(1, null, null, null, " fmabcd1234 ", "other FMZZZZZZZZ", "in", 1000, null);
        service.process(t, TxSource.WEBHOOK);
        verifyStored(7L, "MATCHED");
    }

    @Test
    void codeFieldWithoutPrefix_fallsBackToContent_caseInsensitive() {
        withOrder(donation(PaymentOrderStatus.PENDING));
        BankTransfer t = payload(1, null, null, null, "XX123", "MBVCB.1.fmabcd1234.CT", "in", 1000, null);
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
        service.process(payload(9, "MBBank", null, "0123456789012", null, longContent, "in", 1000, "R"),
                TxSource.RECONCILE);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> account = ArgumentCaptor.forClass(String.class);
        verify(txRepo).insertIgnore(eq("sepay"), eq("9"), eq(7L), eq("MATCHED"), eq("RECONCILE"), eq("in"), eq(1000L), eq(CODE),
                content.capture(), eq("MBBank"), account.capture(), eq("R"), isNull());
        assertThat(content.getValue()).hasSize(500);
        assertThat(account.getValue()).isEqualTo("****9012");
    }

    @Test
    void maskAccount_shortBlankNull() {
        assertThat(PaymentIngestionServiceImpl.maskAccount("123")).isEqualTo("****");
        assertThat(PaymentIngestionServiceImpl.maskAccount(" ")).isNull();
        assertThat(PaymentIngestionServiceImpl.maskAccount(null)).isNull();
    }

    @Test
    void duplicate_insertReturnsZero_noCredit() {
        PaymentOrder order = donation(PaymentOrderStatus.PENDING);
        withOrder(order);
        when(txRepo.insertIgnore(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(), any(),
                any(), any(), any(), any(), any())).thenReturn(0);
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
        PaymentIngestionServiceImpl s = new PaymentIngestionServiceImpl(props, orderRepo, txRepo,
                new PaymentSettlement(orderRepo, planRepo, userRepo, events));
        assertThat(s.extractCode(payload(1, null, null, null, CODE, null, "in", 1, null))).isEqualTo(CODE);
    }

    private static BankTransfer payload(long id, String bank, Instant date, String account, String code,
                                        String content, String type, long amount, String ref) {
        return new BankTransfer("sepay", String.valueOf(id),
                "in".equalsIgnoreCase(type) ? TransferDirection.IN : TransferDirection.OUT,
                amount, code, content, bank, account, ref, date);
    }
}
