package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.payment.AdminOrderDto;
import com.canhlabs.funnyapp.dto.payment.AdminPaymentSummaryDto;
import com.canhlabs.funnyapp.dto.payment.AdminPlanDto;
import com.canhlabs.funnyapp.dto.payment.AdminTransactionDto;
import com.canhlabs.funnyapp.dto.payment.PlanRequest;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.entity.SepayTransaction;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.repo.SepayTransactionRepository;
import com.canhlabs.funnyapp.service.AdminPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {

    static final int MAX_NAME = 100;
    /** Orders still waiting for money: the only ones an admin can mark paid or cancel. */
    private static final Set<PaymentOrderStatus> OPEN = EnumSet.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.EXPIRED);
    /** Placeholder transaction id in settlement logs for an admin-confirmed payment (no SePay transaction). */
    static final long MANUAL_TX_ID = 0L;
    private static final Pattern PLAN_CODE = Pattern.compile("^[A-Z0-9_]{3,32}$");

    private final PaymentOrderRepository orderRepo;
    private final SepayTransactionRepository txRepo;
    private final PremiumPlanRepository planRepo;
    private final PaymentSettlement settlement;
    private final Clock clock;

    @Autowired
    public AdminPaymentServiceImpl(PaymentOrderRepository orderRepo, SepayTransactionRepository txRepo,
                                   PremiumPlanRepository planRepo, PaymentSettlement settlement) {
        this(orderRepo, txRepo, planRepo, settlement, Clock.systemUTC());
    }

    AdminPaymentServiceImpl(PaymentOrderRepository orderRepo, SepayTransactionRepository txRepo,
                            PremiumPlanRepository planRepo, PaymentSettlement settlement, Clock clock) {
        this.orderRepo = orderRepo;
        this.txRepo = txRepo;
        this.planRepo = planRepo;
        this.settlement = settlement;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderDto> listOrders(PaymentType type, PaymentOrderStatus status, Pageable pageable) {
        var statuses = status == null ? EnumSet.allOf(PaymentOrderStatus.class) : EnumSet.of(status);
        Page<PaymentOrder> page = type == null
                ? orderRepo.search(statuses, pageable)
                : orderRepo.searchByType(type, statuses, pageable);
        Map<Long, String> planCodes = planRepo.findAll().stream()
                .collect(Collectors.toMap(PremiumPlan::getId, PremiumPlan::getCode));
        return page.map(o -> toDto(o, planCodes));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminTransactionDto> listUnmatched(Pageable pageable) {
        return txRepo.findByMatchStatusOrderByReceivedAtDesc(TxMatchStatus.UNMATCHED, pageable).map(t ->
                AdminTransactionDto.builder().sepayId(t.getSepayId()).amount(t.getAmount())
                        .content(t.getContent()).code(t.getCode()).gateway(t.getGateway())
                        .transactionDate(t.getTransactionDate()).receivedAt(t.getReceivedAt()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentSummaryDto summary() {
        Instant since = Instant.now(clock).minus(Duration.ofDays(30));
        return AdminPaymentSummaryDto.builder()
                .donationTotal(orderRepo.sumPaid(PaymentType.DONATION))
                .donation30d(orderRepo.sumPaidSince(PaymentType.DONATION, since))
                .premiumTotal(orderRepo.sumPaid(PaymentType.PREMIUM))
                .premium30d(orderRepo.sumPaidSince(PaymentType.PREMIUM, since))
                .pendingOrders(orderRepo.countByStatus(PaymentOrderStatus.PENDING))
                .underpaidOrders(orderRepo.countByStatus(PaymentOrderStatus.UNDERPAID))
                .unmatchedTransactions(txRepo.countByMatchStatus(TxMatchStatus.UNMATCHED))
                .build();
    }

    @Override
    @Transactional
    public AdminOrderDto linkTransaction(long sepayId, String orderCode) {
        String code = orderCode == null ? "" : orderCode.trim().toUpperCase(Locale.ROOT);
        PaymentOrder order = orderRepo.findByCodeForUpdate(code)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4041, "error.payment.orderNotFound"));
        if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
            throw CustomException.of(HttpStatus.CONFLICT, 4093, "error.payment.orderCancelled");
        }
        // Read the transaction only after the order lock: a concurrent link waits above and then sees MATCHED.
        SepayTransaction tx = txRepo.findById(sepayId)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4042, "error.payment.txNotFound"));
        if (tx.getMatchStatus() != TxMatchStatus.UNMATCHED) {
            throw CustomException.of(HttpStatus.CONFLICT, 4091, "error.payment.txNotUnmatched");
        }
        tx.setMatchStatus(TxMatchStatus.MATCHED);
        tx.setOrderId(order.getId());
        txRepo.save(tx);
        settlement.apply(order, tx.getSepayId(), tx.getAmount(), Instant.now(clock));
        log.info("Admin linked tx {} to order {}", sepayId, order.getId());
        return toDto(order, planCodesFor(order));
    }

    @Override
    @Transactional
    public void ignoreTransaction(long sepayId) {
        // Conditional update, so a concurrent link that already set MATCHED is never overwritten
        if (txRepo.updateMatchStatus(sepayId, TxMatchStatus.UNMATCHED, TxMatchStatus.IGNORED) == 1) {
            log.info("Admin ignored tx {}", sepayId);
            return;
        }
        SepayTransaction tx = txRepo.findById(sepayId)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4042, "error.payment.txNotFound"));
        if (tx.getMatchStatus() != TxMatchStatus.IGNORED) {
            throw CustomException.of(HttpStatus.CONFLICT, 4091, "error.payment.txNotUnmatched");
        }
    }

    @Override
    @Transactional
    public AdminOrderDto resolveOrder(long orderId, boolean grantPremium) {
        PaymentOrder order = lockOrder(orderId);
        if (order.getStatus() == PaymentOrderStatus.RESOLVED) {
            return toDto(order, planCodesFor(order));
        }
        if (order.getStatus() != PaymentOrderStatus.UNDERPAID) {
            throw CustomException.of(HttpStatus.CONFLICT, 4092, "error.payment.notUnderpaid");
        }
        if (grantPremium && order.getType() != PaymentType.PREMIUM) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4005, "error.payment.grantNeedsPremium");
        }
        Instant now = Instant.now(clock);
        order.setStatus(PaymentOrderStatus.RESOLVED);
        order.setPaidAt(now);
        if (grantPremium) {
            settlement.extendPremium(order, now);
        }
        orderRepo.save(order);
        log.info("Admin resolved order {} (grantPremium={})", order.getId(), grantPremium);
        return toDto(order, planCodesFor(order));
    }

    @Override
    @Transactional
    public AdminOrderDto markPaid(long orderId, long amount) {
        if (amount <= 0) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4006, "error.payment.manualAmountInvalid");
        }
        PaymentOrder order = lockOrder(orderId);
        if (!OPEN.contains(order.getStatus())) {
            throw CustomException.of(HttpStatus.CONFLICT, 4095, "error.payment.notOpen");
        }
        settlement.apply(order, MANUAL_TX_ID, amount, Instant.now(clock));
        log.info("Admin marked order {} paid manually: {} VND -> {}", order.getId(), amount, order.getStatus());
        return toDto(order, planCodesFor(order));
    }

    @Override
    @Transactional
    public AdminOrderDto cancelOrder(long orderId) {
        PaymentOrder order = lockOrder(orderId);
        if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
            return toDto(order, planCodesFor(order));
        }
        if (!OPEN.contains(order.getStatus())) {
            throw CustomException.of(HttpStatus.CONFLICT, 4095, "error.payment.notOpen");
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        orderRepo.save(order);
        log.info("Admin cancelled order {}", order.getId());
        return toDto(order, planCodesFor(order));
    }

    /** Row lock by code, the same lock the webhook takes, so a manual action and a transfer cannot race. */
    private PaymentOrder lockOrder(long orderId) {
        PaymentOrder found = orderRepo.findById(orderId)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4041, "error.payment.orderNotFound"));
        return orderRepo.findByCodeForUpdate(found.getCode()).orElse(found);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminPlanDto> listPlans() {
        return planRepo.findAllByOrderBySortOrderAscIdAsc().stream().map(AdminPaymentServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public AdminPlanDto createPlan(PlanRequest request) {
        validate(request);
        String code = request.getCode().trim();
        if (planRepo.existsByCode(code)) {
            throw CustomException.of(HttpStatus.CONFLICT, 4094, "error.payment.planCodeTaken");
        }
        PremiumPlan plan = PremiumPlan.builder().build();
        fill(plan, request, code);
        return toDto(planRepo.save(plan));
    }

    @Override
    @Transactional
    public AdminPlanDto updatePlan(long id, PlanRequest request) {
        PremiumPlan plan = findPlan(id);
        validate(request);
        String code = request.getCode().trim();
        if (!code.equals(plan.getCode()) && planRepo.existsByCode(code)) {
            throw CustomException.of(HttpStatus.CONFLICT, 4094, "error.payment.planCodeTaken");
        }
        fill(plan, request, code);
        return toDto(planRepo.save(plan));
    }

    @Override
    @Transactional
    public AdminPlanDto setPlanActive(long id, boolean active) {
        PremiumPlan plan = findPlan(id);
        plan.setActive(active);
        return toDto(planRepo.save(plan));
    }

    private PremiumPlan findPlan(long id) {
        return planRepo.findById(id)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 4043, "error.payment.planNotFound"));
    }

    private static void fill(PremiumPlan plan, PlanRequest r, String code) {
        plan.setCode(code);
        plan.setName(r.getName().trim());
        plan.setDurationDays(r.getDurationDays());
        plan.setPriceVnd(r.getPriceVnd());
        if (r.getActive() != null) {
            plan.setActive(r.getActive());
        }
        if (r.getSortOrder() != null) {
            plan.setSortOrder(r.getSortOrder());
        }
    }

    private static void validate(PlanRequest r) {
        if (r == null || r.getCode() == null || !PLAN_CODE.matcher(r.getCode().trim()).matches()) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4006, "error.payment.planCodeInvalid");
        }
        if (r.getName() == null || r.getName().isBlank() || r.getName().trim().length() > MAX_NAME) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4007, "error.payment.planNameInvalid");
        }
        if (r.getPriceVnd() == null || r.getPriceVnd() <= 0) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4008, "error.payment.planPriceInvalid");
        }
        if (r.getDurationDays() == null || r.getDurationDays() <= 0) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4009, "error.payment.planDurationInvalid");
        }
    }

    private Map<Long, String> planCodesFor(PaymentOrder order) {
        if (order.getPlanId() == null) {
            return Map.of();
        }
        return planRepo.findById(order.getPlanId()).map(p -> Map.of(p.getId(), p.getCode())).orElse(Map.of());
    }

    private static AdminOrderDto toDto(PaymentOrder o, Map<Long, String> planCodes) {
        long surplus = o.getType() == PaymentType.PREMIUM && o.getExpectedAmount() != null
                ? Math.max(0, o.getPaidAmount() - o.getExpectedAmount()) : 0;
        return AdminOrderDto.builder()
                .id(o.getId()).code(o.getCode()).type(o.getType()).status(o.getStatus())
                .expectedAmount(o.getExpectedAmount()).paidAmount(o.getPaidAmount()).surplus(surplus)
                .donorName(o.getDonorName()).message(o.getMessage()).userId(o.getUserId())
                .planCode(o.getPlanId() == null ? null : planCodes.get(o.getPlanId()))
                .createdAt(o.getCreatedAt()).paidAt(o.getPaidAt()).expiresAt(o.getExpiresAt())
                .build();
    }

    private static AdminPlanDto toDto(PremiumPlan p) {
        return AdminPlanDto.builder().id(p.getId()).code(p.getCode()).name(p.getName())
                .durationDays(p.getDurationDays()).priceVnd(p.getPriceVnd())
                .active(p.isActive()).sortOrder(p.getSortOrder()).build();
    }
}
