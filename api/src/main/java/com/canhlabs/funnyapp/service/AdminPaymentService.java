package com.canhlabs.funnyapp.service;

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

    /** UNDERPAID to RESOLVED, optionally granting Premium. Calling again on a RESOLVED order is a no-op. */
    AdminOrderDto resolveOrder(long orderId, boolean grantPremium);

    List<AdminPlanDto> listPlans();

    AdminPlanDto createPlan(PlanRequest request);

    AdminPlanDto updatePlan(long id, PlanRequest request);

    AdminPlanDto setPlanActive(long id, boolean active);
}
