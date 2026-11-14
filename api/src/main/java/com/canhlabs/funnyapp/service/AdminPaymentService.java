package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.payment.AdminOrderCheckDto;
import com.canhlabs.funnyapp.dto.payment.AdminOrderDto;
import com.canhlabs.funnyapp.dto.payment.AdminPaymentSummaryDto;
import com.canhlabs.funnyapp.dto.payment.AdminPlanDto;
import com.canhlabs.funnyapp.dto.payment.AdminTransactionDto;
import com.canhlabs.funnyapp.dto.payment.PlanRequest;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Admin view and manual fixes for donations and Premium payments (ADR-0037). */
public interface AdminPaymentService {

    /** Newest first; null filters mean "all". */
    Page<AdminOrderDto> listOrders(PaymentType type, PaymentOrderStatus status, Pageable pageable);

    Page<AdminTransactionDto> listUnmatched(Pageable pageable);

    AdminPaymentSummaryDto summary();

    /** Attaches an UNMATCHED transfer to an order and applies the webhook amount rules. */
    AdminOrderDto linkTransaction(long sepayId, String orderCode);

    /**
     * UNMATCHED to IGNORED: a transfer no order owns (SePay test send, unrelated income); it leaves the
     * unmatched list but stays stored. Calling again on an IGNORED transfer is a no-op.
     */
    void ignoreTransaction(long sepayId);

    /** UNDERPAID to RESOLVED, optionally granting Premium. Calling again on a RESOLVED order is a no-op. */
    AdminOrderDto resolveOrder(long orderId, boolean grantPremium);

    /**
     * PENDING or EXPIRED order the admin confirmed from the bank statement (no webhook arrived):
     * applies {@code amount} with the webhook amount rules, so it lands on PAID or UNDERPAID.
     */
    AdminOrderDto markPaid(long orderId, long amount);

    /**
     * Asks SePay now for transfers carrying this order's code (PENDING, EXPIRED or UNDERPAID) and records any the
     * webhook missed, with the webhook amount rules.
     */
    AdminOrderCheckDto checkOrderOnSepay(long orderId);

    /** PENDING or EXPIRED to CANCELLED; a later transfer with its code is then stored UNMATCHED. */
    AdminOrderDto cancelOrder(long orderId);

    List<AdminPlanDto> listPlans();

    AdminPlanDto createPlan(PlanRequest request);

    AdminPlanDto updatePlan(long id, PlanRequest request);

    AdminPlanDto setPlanActive(long id, boolean active);
}
