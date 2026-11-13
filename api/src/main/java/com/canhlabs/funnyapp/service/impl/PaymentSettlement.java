package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.event.PaymentEvent;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * The single place where money lands on an order: amount rules (PAID / UNDERPAID) and the Premium
 * entitlement. Used by the SePay webhook and by admin actions (link a transfer, resolve an order).
 * Callers hold the order row lock and an open transaction.
 */
@Slf4j
@Component
public class PaymentSettlement {

    private static final Set<PaymentOrderStatus> SETTLED = Set.of(PaymentOrderStatus.PAID, PaymentOrderStatus.RESOLVED);

    private final PaymentOrderRepository orderRepo;
    private final PremiumPlanRepository planRepo;
    private final UserRepo userRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentSettlement(PaymentOrderRepository orderRepo, PremiumPlanRepository planRepo,
                             UserRepo userRepo, ApplicationEventPublisher eventPublisher) {
        this.orderRepo = orderRepo;
        this.planRepo = planRepo;
        this.userRepo = userRepo;
        this.eventPublisher = eventPublisher;
    }

    /** Adds {@code amount} to the order and moves it to PAID or UNDERPAID unless it is already settled. */
    public void apply(PaymentOrder order, long txId, long amount, Instant now) {
        PaymentOrderStatus before = order.getStatus();
        order.setPaidAmount(Math.addExact(order.getPaidAmount(), amount));
        if (!SETTLED.contains(before)) {
            Long expected = order.getExpectedAmount();
            // A donation is PAID whatever arrives; only Premium compares against the price
            if (order.getType() != PaymentType.PREMIUM || expected == null || order.getPaidAmount() >= expected) {
                order.setStatus(PaymentOrderStatus.PAID);
                order.setPaidAt(now);
                if (order.getType() == PaymentType.PREMIUM) {
                    extendPremium(order, now);
                }
            } else {
                order.setStatus(PaymentOrderStatus.UNDERPAID);
            }
        }
        orderRepo.save(order);
        PaymentOrderStatus after = order.getStatus();
        log.info("SePay tx {} applied to order {}: {} -> {}", txId, order.getId(), before, after);
        if (after != before && (after == PaymentOrderStatus.PAID || after == PaymentOrderStatus.UNDERPAID)) {
            eventPublisher.publishEvent(new PaymentEvent(order.getId(), order.getType(), after, amount));
        }
    }

    /** Extends the buyer's Premium by the plan duration, stacking on any time still left. */
    public void extendPremium(PaymentOrder order, Instant now) {
        Optional<PremiumPlan> plan = order.getPlanId() == null ? Optional.empty() : planRepo.findById(order.getPlanId());
        Optional<User> user = order.getUserId() == null ? Optional.empty() : userRepo.findByIdForUpdate(order.getUserId());
        if (plan.isEmpty() || user.isEmpty()) {
            log.error("Premium order {} paid but plan or user is missing (planId={}, userId={}); entitlement NOT granted, needs admin action",
                    order.getId(), order.getPlanId(), order.getUserId());
            return;
        }
        Instant current = user.get().getPremiumUntil();
        Instant base = current != null && current.isAfter(now) ? current : now;
        user.get().setPremiumUntil(base.plus(Duration.ofDays(plan.get().getDurationDays())));
        userRepo.save(user.get());
    }
}
