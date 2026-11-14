package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.payment.AdminOrderDto;
import com.canhlabs.funnyapp.dto.payment.AdminPaymentSummaryDto;
import com.canhlabs.funnyapp.dto.payment.AdminPlanDto;
import com.canhlabs.funnyapp.dto.payment.PlanRequest;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.entity.SepayTransaction;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminPaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private PaymentOrderRepository orderRepo;
    private SepayTransactionRepository txRepo;
    private PremiumPlanRepository planRepo;
    private UserRepo userRepo;
    private ApplicationEventPublisher events;
    private AdminPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepo = mock(PaymentOrderRepository.class);
        txRepo = mock(SepayTransactionRepository.class);
        planRepo = mock(PremiumPlanRepository.class);
        userRepo = mock(UserRepo.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AdminPaymentServiceImpl(orderRepo, txRepo, planRepo,
                new PaymentSettlement(orderRepo, planRepo, userRepo, events), Clock.fixed(NOW, ZoneOffset.UTC));
        when(orderRepo.save(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(txRepo.save(any(SepayTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(planRepo.save(any(PremiumPlan.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static PaymentOrder premiumOrder(PaymentOrderStatus status, long paid) {
        return PaymentOrder.builder().id(5L).code("FMABCD1234").type(PaymentType.PREMIUM).status(status)
                .userId(8L).planId(3L).expectedAmount(100000L).paidAmount(paid).expiresAt(NOW).build();
    }

    private static SepayTransaction tx(TxMatchStatus status, long amount) {
        return SepayTransaction.builder().sepayId(9L).matchStatus(status).amount(amount).build();
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    private void stubPlanAndUser(User user) {
        when(planRepo.findById(3L)).thenReturn(Optional.of(PremiumPlan.builder().id(3L).code("P1").durationDays(30).build()));
        when(userRepo.findByIdForUpdate(8L)).thenReturn(Optional.of(user));
    }

    // ---- orders / transactions / summary ----

    @Test
    void listOrders_noFilter_usesAllStatusesAndComputesSurplus() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PAID, 130000);
        when(planRepo.findAll()).thenReturn(List.of(PremiumPlan.builder().id(3L).code("P1").build()));
        PageRequest pr = PageRequest.of(0, 20);
        when(orderRepo.search(EnumSet.allOf(PaymentOrderStatus.class), pr)).thenReturn(new PageImpl<>(List.of(order)));

        AdminOrderDto dto = service.listOrders(null, null, pr).getContent().get(0);

        assertThat(dto.getSurplus()).isEqualTo(30000);
        assertThat(dto.getPlanCode()).isEqualTo("P1");
    }

    @Test
    void listOrders_withTypeAndStatus_usesTypeQuery_donationHasNoSurplus() {
        PaymentOrder donation = PaymentOrder.builder().id(1L).code("C").type(PaymentType.DONATION)
                .status(PaymentOrderStatus.PAID).paidAmount(50000).expiresAt(NOW).build();
        PageRequest pr = PageRequest.of(0, 20);
        when(planRepo.findAll()).thenReturn(List.of());
        when(orderRepo.searchByType(PaymentType.DONATION, EnumSet.of(PaymentOrderStatus.PAID), pr))
                .thenReturn(new PageImpl<>(List.of(donation)));

        AdminOrderDto dto = service.listOrders(PaymentType.DONATION, PaymentOrderStatus.PAID, pr).getContent().get(0);

        assertThat(dto.getSurplus()).isZero();
        assertThat(dto.getPlanCode()).isNull();
    }

    @Test
    void premiumUnderpaid_hasZeroSurplus() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        when(planRepo.findAll()).thenReturn(List.of());
        PageRequest pr = PageRequest.of(0, 20);
        when(orderRepo.search(EnumSet.allOf(PaymentOrderStatus.class), pr)).thenReturn(new PageImpl<>(List.of(order)));

        assertThat(service.listOrders(null, null, pr).getContent().get(0).getSurplus()).isZero();
    }

    @Test
    void listUnmatched_mapsFields() {
        SepayTransaction t = SepayTransaction.builder().sepayId(9L).matchStatus(TxMatchStatus.UNMATCHED).amount(7000)
                .content("memo").code("C1").gateway("MB").build();
        PageRequest pr = PageRequest.of(0, 20);
        when(txRepo.findByMatchStatusOrderByReceivedAtDesc(TxMatchStatus.UNMATCHED, pr))
                .thenReturn(new PageImpl<>(List.of(t)));

        var dto = service.listUnmatched(pr).getContent().get(0);

        assertThat(dto.getSepayId()).isEqualTo(9L);
        assertThat(dto.getAmount()).isEqualTo(7000);
        assertThat(dto.getGateway()).isEqualTo("MB");
    }

    @Test
    void summary_combinesTotalsThirtyDayWindowAndCounts() {
        Instant since = NOW.minusSeconds(30L * 24 * 3600);
        when(orderRepo.sumPaid(PaymentType.DONATION)).thenReturn(1000L);
        when(orderRepo.sumPaidSince(PaymentType.DONATION, since)).thenReturn(100L);
        when(orderRepo.sumPaid(PaymentType.PREMIUM)).thenReturn(5000L);
        when(orderRepo.sumPaidSince(PaymentType.PREMIUM, since)).thenReturn(500L);
        when(orderRepo.countByStatus(PaymentOrderStatus.PENDING)).thenReturn(4L);
        when(orderRepo.countByStatus(PaymentOrderStatus.UNDERPAID)).thenReturn(2L);
        when(txRepo.countByMatchStatus(TxMatchStatus.UNMATCHED)).thenReturn(3L);

        AdminPaymentSummaryDto s = service.summary();

        assertThat(s.getDonationTotal()).isEqualTo(1000);
        assertThat(s.getDonation30d()).isEqualTo(100);
        assertThat(s.getPremiumTotal()).isEqualTo(5000);
        assertThat(s.getPremium30d()).isEqualTo(500);
        assertThat(s.getPendingOrders()).isEqualTo(4);
        assertThat(s.getUnderpaidOrders()).isEqualTo(2);
        assertThat(s.getUnmatchedTransactions()).isEqualTo(3);
    }

    // ---- link ----

    @Test
    void link_full_marksPaidAndExtendsPremium() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PENDING, 0);
        SepayTransaction t = tx(TxMatchStatus.UNMATCHED, 100000);
        User user = new User();
        stubPlanAndUser(user);
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));
        when(txRepo.findById(9L)).thenReturn(Optional.of(t));

        AdminOrderDto dto = service.linkTransaction(9L, " fmabcd1234 ");

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(t.getMatchStatus()).isEqualTo(TxMatchStatus.MATCHED);
        assertThat(t.getOrderId()).isEqualTo(5L);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(user.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
    }

    @Test
    void link_partial_marksUnderpaidWithoutPremium() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PENDING, 0);
        SepayTransaction t = tx(TxMatchStatus.UNMATCHED, 40000);
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));
        when(txRepo.findById(9L)).thenReturn(Optional.of(t));

        AdminOrderDto dto = service.linkTransaction(9L, "FMABCD1234");

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
        assertThat(dto.getPaidAmount()).isEqualTo(40000);
        verifyNoInteractions(userRepo);
    }

    @Test
    void link_secondTransferTopsUpUnderpaidOrder() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        SepayTransaction t = tx(TxMatchStatus.UNMATCHED, 60000);
        stubPlanAndUser(new User());
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));
        when(txRepo.findById(9L)).thenReturn(Optional.of(t));

        assertThat(service.linkTransaction(9L, "FMABCD1234").getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(100000);
    }

    @Test
    void link_toAlreadyPaidOrder_addsAmountButDoesNotGrantAgain() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PAID, 100000);
        SepayTransaction t = tx(TxMatchStatus.UNMATCHED, 5000);
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));
        when(txRepo.findById(9L)).thenReturn(Optional.of(t));

        AdminOrderDto dto = service.linkTransaction(9L, "FMABCD1234");

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(dto.getPaidAmount()).isEqualTo(105000);
        verifyNoInteractions(userRepo);
    }

    @Test
    void link_calledTwice_secondIsConflictAndDoesNotDoubleApply() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PENDING, 0);
        SepayTransaction t = tx(TxMatchStatus.UNMATCHED, 40000);
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));
        when(txRepo.findById(9L)).thenReturn(Optional.of(t));

        service.linkTransaction(9L, "FMABCD1234");
        assertStatus(() -> service.linkTransaction(9L, "FMABCD1234"), HttpStatus.CONFLICT);

        assertThat(order.getPaidAmount()).isEqualTo(40000);
    }

    @Test
    void link_rejections() {
        when(orderRepo.findByCodeForUpdate("NOPE")).thenReturn(Optional.empty());
        assertStatus(() -> service.linkTransaction(9L, "NOPE"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.linkTransaction(9L, null), HttpStatus.NOT_FOUND);

        when(orderRepo.findByCodeForUpdate("FMCANCEL01"))
                .thenReturn(Optional.of(premiumOrder(PaymentOrderStatus.CANCELLED, 0)));
        assertStatus(() -> service.linkTransaction(9L, "FMCANCEL01"), HttpStatus.CONFLICT);

        when(orderRepo.findByCodeForUpdate("FMABCD1234"))
                .thenReturn(Optional.of(premiumOrder(PaymentOrderStatus.PENDING, 0)));
        when(txRepo.findById(9L)).thenReturn(Optional.empty());
        assertStatus(() -> service.linkTransaction(9L, "FMABCD1234"), HttpStatus.NOT_FOUND);

        when(txRepo.findById(9L)).thenReturn(Optional.of(tx(TxMatchStatus.IGNORED, 1)));
        assertStatus(() -> service.linkTransaction(9L, "FMABCD1234"), HttpStatus.CONFLICT);
        verify(orderRepo, never()).save(any());
    }

    // ---- ignore ----

    @Test
    void ignore_unmatched_changesStatusOnly() {
        when(txRepo.updateMatchStatus(9L, TxMatchStatus.UNMATCHED, TxMatchStatus.IGNORED)).thenReturn(1);

        service.ignoreTransaction(9L);

        verify(txRepo).updateMatchStatus(9L, TxMatchStatus.UNMATCHED, TxMatchStatus.IGNORED);
        verify(txRepo, never()).findById(any());
        verifyNoInteractions(orderRepo);
    }

    @Test
    void ignore_alreadyIgnored_isNoOp() {
        when(txRepo.updateMatchStatus(9L, TxMatchStatus.UNMATCHED, TxMatchStatus.IGNORED)).thenReturn(0);
        when(txRepo.findById(9L)).thenReturn(Optional.of(tx(TxMatchStatus.IGNORED, 10000)));

        service.ignoreTransaction(9L);

        verifyNoInteractions(orderRepo);
    }

    @Test
    void ignore_rejections() {
        when(txRepo.updateMatchStatus(9L, TxMatchStatus.UNMATCHED, TxMatchStatus.IGNORED)).thenReturn(0);
        when(txRepo.findById(9L)).thenReturn(Optional.empty());
        assertStatus(() -> service.ignoreTransaction(9L), HttpStatus.NOT_FOUND);

        when(txRepo.findById(9L)).thenReturn(Optional.of(tx(TxMatchStatus.MATCHED, 10000)));
        assertStatus(() -> service.ignoreTransaction(9L), HttpStatus.CONFLICT);
    }

    // ---- resolve ----

    @Test
    void resolve_underpaidWithGrant_extendsPremium() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        User user = new User();
        stubPlanAndUser(user);
        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));

        AdminOrderDto dto = service.resolveOrder(5L, true);

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.RESOLVED);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(user.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
    }

    @Test
    void resolve_withoutGrant_leavesUserAlone() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));

        assertThat(service.resolveOrder(5L, false).getStatus()).isEqualTo(PaymentOrderStatus.RESOLVED);
        verifyNoInteractions(userRepo);
    }

    @Test
    void resolve_isIdempotent_secondCallDoesNotGrantAgain() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        User user = new User();
        stubPlanAndUser(user);
        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(order));

        service.resolveOrder(5L, true);
        Instant first = user.getPremiumUntil();
        AdminOrderDto again = service.resolveOrder(5L, true);

        assertThat(again.getStatus()).isEqualTo(PaymentOrderStatus.RESOLVED);
        assertThat(user.getPremiumUntil()).isEqualTo(first);
    }

    @Test
    void resolve_rejections() {
        when(orderRepo.findById(404L)).thenReturn(Optional.empty());
        assertStatus(() -> service.resolveOrder(404L, false), HttpStatus.NOT_FOUND);

        PaymentOrder paid = premiumOrder(PaymentOrderStatus.PAID, 100000);
        when(orderRepo.findById(5L)).thenReturn(Optional.of(paid));
        when(orderRepo.findByCodeForUpdate("FMABCD1234")).thenReturn(Optional.of(paid));
        assertStatus(() -> service.resolveOrder(5L, false), HttpStatus.CONFLICT);

        PaymentOrder donation = PaymentOrder.builder().id(6L).code("FMDONATE01").type(PaymentType.DONATION)
                .status(PaymentOrderStatus.UNDERPAID).expectedAmount(50000L).paidAmount(10000).expiresAt(NOW).build();
        when(orderRepo.findById(6L)).thenReturn(Optional.of(donation));
        when(orderRepo.findByCodeForUpdate("FMDONATE01")).thenReturn(Optional.of(donation));
        assertStatus(() -> service.resolveOrder(6L, true), HttpStatus.BAD_REQUEST);
        assertThat(donation.getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
    }

    // ---- mark paid / cancel ----

    private void stubLock(PaymentOrder order) {
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepo.findByCodeForUpdate(order.getCode())).thenReturn(Optional.of(order));
    }

    @Test
    void markPaid_expiredPremiumFullAmount_paysAndGrantsPremium() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.EXPIRED, 0);
        User user = new User();
        stubPlanAndUser(user);
        stubLock(order);

        AdminOrderDto dto = service.markPaid(5L, 100000L);

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(100000L);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(user.getPremiumUntil()).isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
    }

    @Test
    void markPaid_pendingPremiumShortAmount_isUnderpaid() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PENDING, 0);
        stubLock(order);

        assertThat(service.markPaid(5L, 40000L).getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
        verifyNoInteractions(userRepo);
    }

    @Test
    void markPaid_pendingDonation_isPaid() {
        PaymentOrder order = PaymentOrder.builder().id(6L).code("FMDONATE01").type(PaymentType.DONATION)
                .status(PaymentOrderStatus.PENDING).paidAmount(0).expiresAt(NOW).build();
        stubLock(order);

        assertThat(service.markPaid(6L, 10000L).getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getPaidAmount()).isEqualTo(10000L);
    }

    @Test
    void markPaid_donationBelowChosenAmount_isStillPaid() {
        PaymentOrder order = PaymentOrder.builder().id(6L).code("FMDONATE01").type(PaymentType.DONATION)
                .status(PaymentOrderStatus.PENDING).expectedAmount(50000L).paidAmount(0).expiresAt(NOW).build();
        stubLock(order);

        AdminOrderDto dto = service.markPaid(6L, 20000L);

        assertThat(dto.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(dto.getExpectedAmount()).isEqualTo(50000L);
        assertThat(dto.getSurplus()).isZero();
    }

    @Test
    void markPaid_rejections() {
        assertStatus(() -> service.markPaid(5L, 0L), HttpStatus.BAD_REQUEST);

        when(orderRepo.findById(404L)).thenReturn(Optional.empty());
        assertStatus(() -> service.markPaid(404L, 1000L), HttpStatus.NOT_FOUND);

        PaymentOrder paid = premiumOrder(PaymentOrderStatus.PAID, 100000);
        stubLock(paid);
        assertStatus(() -> service.markPaid(5L, 1000L), HttpStatus.CONFLICT);
        assertThat(paid.getPaidAmount()).isEqualTo(100000L);
    }

    @Test
    void cancel_pending_isCancelledAndIdempotent() {
        PaymentOrder order = premiumOrder(PaymentOrderStatus.PENDING, 0);
        stubLock(order);

        assertThat(service.cancelOrder(5L).getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
        assertThat(service.cancelOrder(5L).getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void cancel_paidOrUnderpaid_isConflict() {
        PaymentOrder underpaid = premiumOrder(PaymentOrderStatus.UNDERPAID, 40000);
        stubLock(underpaid);

        assertStatus(() -> service.cancelOrder(5L), HttpStatus.CONFLICT);
        assertThat(underpaid.getStatus()).isEqualTo(PaymentOrderStatus.UNDERPAID);
    }

    // ---- plans ----

    private static PlanRequest plan(String code, String name, Integer days, Long price) {
        PlanRequest r = new PlanRequest();
        r.setCode(code);
        r.setName(name);
        r.setDurationDays(days);
        r.setPriceVnd(price);
        return r;
    }

    @Test
    void listPlans_mapsAll() {
        when(planRepo.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                PremiumPlan.builder().id(1L).code("PLAN_A").name("A").durationDays(30).priceVnd(1000).active(true).sortOrder(2).build()));

        AdminPlanDto dto = service.listPlans().get(0);

        assertThat(dto.getCode()).isEqualTo("PLAN_A");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getSortOrder()).isEqualTo(2);
    }

    @Test
    void createPlan_valid_savesInactiveByDefault() {
        when(planRepo.existsByCode("PREMIUM_1Y")).thenReturn(false);

        AdminPlanDto dto = service.createPlan(plan(" PREMIUM_1Y ", " 1 year ", 365, 990000L));

        assertThat(dto.getCode()).isEqualTo("PREMIUM_1Y");
        assertThat(dto.getName()).isEqualTo("1 year");
        assertThat(dto.isActive()).isFalse();
        assertThat(dto.getPriceVnd()).isEqualTo(990000);
    }

    @Test
    void createPlan_honoursActiveAndSortOrder() {
        PlanRequest r = plan("PREMIUM_1Y", "1 year", 365, 990000L);
        r.setActive(true);
        r.setSortOrder(4);

        AdminPlanDto dto = service.createPlan(r);

        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getSortOrder()).isEqualTo(4);
    }

    @Test
    void createPlan_duplicateCode_conflict() {
        when(planRepo.existsByCode("PREMIUM_1Y")).thenReturn(true);
        assertStatus(() -> service.createPlan(plan("PREMIUM_1Y", "x", 1, 1L)), HttpStatus.CONFLICT);
        verify(planRepo, never()).save(any());
    }

    @Test
    void planValidation_rejectsBadInput() {
        String longName = "n".repeat(AdminPaymentServiceImpl.MAX_NAME + 1);
        List<PlanRequest> bad = List.of(
                plan(null, "x", 1, 1L), plan("ab", "x", 1, 1L), plan("lower_case", "x", 1, 1L),
                plan("A".repeat(33), "x", 1, 1L), plan("BAD-CODE", "x", 1, 1L),
                plan("GOOD_CODE", null, 1, 1L), plan("GOOD_CODE", "  ", 1, 1L), plan("GOOD_CODE", longName, 1, 1L),
                plan("GOOD_CODE", "x", 1, null), plan("GOOD_CODE", "x", 1, 0L), plan("GOOD_CODE", "x", 1, -5L),
                plan("GOOD_CODE", "x", null, 1L), plan("GOOD_CODE", "x", 0, 1L), plan("GOOD_CODE", "x", -1, 1L));
        for (PlanRequest r : bad) {
            assertStatus(() -> service.createPlan(r), HttpStatus.BAD_REQUEST);
        }
        assertStatus(() -> service.createPlan(null), HttpStatus.BAD_REQUEST);
        verify(planRepo, never()).save(any());
    }

    @Test
    void planValidation_acceptsBoundaries() {
        service.createPlan(plan("ABC", "n".repeat(AdminPaymentServiceImpl.MAX_NAME), 1, 1L));
        service.createPlan(plan("A".repeat(32), "x", 1, 1L));
        verify(planRepo, org.mockito.Mockito.times(2)).save(any(PremiumPlan.class));
    }

    @Test
    void updatePlan_changesFields_keepsSameCodeWithoutUniquenessCheck() {
        PremiumPlan existing = PremiumPlan.builder().id(2L).code("PLAN_A").name("old").durationDays(30).priceVnd(1).active(true).sortOrder(3).build();
        when(planRepo.findById(2L)).thenReturn(Optional.of(existing));

        AdminPlanDto dto = service.updatePlan(2L, plan("PLAN_A", "new", 60, 2000L));

        assertThat(dto.getName()).isEqualTo("new");
        assertThat(dto.getDurationDays()).isEqualTo(60);
        assertThat(dto.getPriceVnd()).isEqualTo(2000);
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getSortOrder()).isEqualTo(3);
        verify(planRepo, never()).existsByCode(any());
    }

    @Test
    void updatePlan_codeChangeToTakenCode_conflict() {
        when(planRepo.findById(2L)).thenReturn(Optional.of(PremiumPlan.builder().id(2L).code("PLAN_A").build()));
        when(planRepo.existsByCode("PLAN_B")).thenReturn(true);
        assertStatus(() -> service.updatePlan(2L, plan("PLAN_B", "x", 1, 1L)), HttpStatus.CONFLICT);
    }

    @Test
    void updatePlan_codeChangeToFreeCode_ok() {
        when(planRepo.findById(2L)).thenReturn(Optional.of(PremiumPlan.builder().id(2L).code("PLAN_A").build()));
        when(planRepo.existsByCode("PLAN_B")).thenReturn(false);
        assertThat(service.updatePlan(2L, plan("PLAN_B", "x", 1, 1L)).getCode()).isEqualTo("PLAN_B");
    }

    @Test
    void updatePlan_unknownOrInvalid() {
        when(planRepo.findById(99L)).thenReturn(Optional.empty());
        assertStatus(() -> service.updatePlan(99L, plan("PLAN_A", "x", 1, 1L)), HttpStatus.NOT_FOUND);

        when(planRepo.findById(2L)).thenReturn(Optional.of(PremiumPlan.builder().id(2L).code("PLAN_A").build()));
        assertStatus(() -> service.updatePlan(2L, plan("PLAN_A", "x", 1, 0L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void setPlanActive_togglesAndRejectsUnknown() {
        PremiumPlan p = PremiumPlan.builder().id(2L).code("PLAN_A").build();
        when(planRepo.findById(2L)).thenReturn(Optional.of(p));
        when(planRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.setPlanActive(2L, true).isActive()).isTrue();
        assertThat(service.setPlanActive(2L, false).isActive()).isFalse();
        assertStatus(() -> service.setPlanActive(99L, true), HttpStatus.NOT_FOUND);
    }

    @Test
    void publicConstructor_usesSystemClock() {
        AdminPaymentServiceImpl s = new AdminPaymentServiceImpl(orderRepo, txRepo, planRepo,
                new PaymentSettlement(orderRepo, planRepo, userRepo, events));
        when(planRepo.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        assertThat(s.listPlans()).isEmpty();
    }
}
