package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.dto.payment.AdminOrderCheckDto;
import com.canhlabs.funnyapp.dto.payment.AdminOrderDto;
import com.canhlabs.funnyapp.dto.payment.AdminPaymentSummaryDto;
import com.canhlabs.funnyapp.dto.payment.AdminPlanDto;
import com.canhlabs.funnyapp.dto.payment.AdminTransactionDto;
import com.canhlabs.funnyapp.dto.payment.LinkTransactionRequest;
import com.canhlabs.funnyapp.dto.payment.MarkPaidRequest;
import com.canhlabs.funnyapp.dto.payment.PlanActiveRequest;
import com.canhlabs.funnyapp.dto.payment.PlanRequest;
import com.canhlabs.funnyapp.dto.payment.ResolveOrderRequest;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.AdminPaymentService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin donations and Premium payments: orders, unmatched transfers, totals, manual fixes, plans (ADR-0037). */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    static final int MAX_PAGE_SIZE = 100;

    private final AdminPaymentService service;

    public AdminPaymentController(AdminPaymentService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<Page<AdminOrderDto>>> orders(
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.listOrders(type, status, pageable(page, size)));
    }

    @GetMapping("/transactions/unmatched")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<Page<AdminTransactionDto>>> unmatched(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.listUnmatched(pageable(page, size)));
    }

    @GetMapping("/summary")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<AdminPaymentSummaryDto>> summary() {
        return ok(service.summary());
    }

    @PostMapping("/transactions/{sepayId}/link")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminLinkPaymentTransaction")
    public ResponseEntity<ResultObjectInfo<AdminOrderDto>> link(@PathVariable long sepayId,
                                                                 @RequestBody LinkTransactionRequest request) {
        return ok(service.linkTransaction(sepayId, request.getOrderCode()));
    }

    @PostMapping("/transactions/{sepayId}/ignore")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminIgnorePaymentTransaction")
    public ResponseEntity<ResultObjectInfo<Void>> ignore(@PathVariable long sepayId) {
        service.ignoreTransaction(sepayId);
        return ok(null);
    }

    @PostMapping("/orders/{id}/resolve")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminResolvePaymentOrder")
    public ResponseEntity<ResultObjectInfo<AdminOrderDto>> resolve(@PathVariable long id,
                                                                    @RequestBody ResolveOrderRequest request) {
        return ok(service.resolveOrder(id, request.isGrantPremium()));
    }

    @PostMapping("/orders/{id}/mark-paid")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminMarkPaymentOrderPaid")
    public ResponseEntity<ResultObjectInfo<AdminOrderDto>> markPaid(@PathVariable long id,
                                                                     @RequestBody MarkPaidRequest request) {
        return ok(service.markPaid(id, request.getAmount()));
    }

    @PostMapping("/orders/{id}/check-sepay")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminCheckPaymentOrderOnSepay")
    public ResponseEntity<ResultObjectInfo<AdminOrderCheckDto>> checkOnSepay(@PathVariable long id) {
        return ok(service.checkOrderOnSepay(id));
    }

    @PostMapping("/orders/{id}/cancel")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminCancelPaymentOrder")
    public ResponseEntity<ResultObjectInfo<AdminOrderDto>> cancel(@PathVariable long id) {
        return ok(service.cancelOrder(id));
    }

    @GetMapping("/plans")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultListInfo<AdminPlanDto>> plans() {
        return ResponseEntity.ok(ResultListInfo.<AdminPlanDto>builder()
                .status(ResultStatus.SUCCESS).data(service.listPlans()).build());
    }

    @PostMapping("/plans")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminCreatePremiumPlan")
    public ResponseEntity<ResultObjectInfo<AdminPlanDto>> createPlan(@RequestBody PlanRequest request) {
        return ok(service.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminUpdatePremiumPlan")
    public ResponseEntity<ResultObjectInfo<AdminPlanDto>> updatePlan(@PathVariable long id,
                                                                      @RequestBody PlanRequest request) {
        return ok(service.updatePlan(id, request));
    }

    @PatchMapping("/plans/{id}/active")
    @HasPermission(perm = Permission.ADMIN)
    @AuditLog("adminSetPremiumPlanActive")
    public ResponseEntity<ResultObjectInfo<AdminPlanDto>> setActive(@PathVariable long id,
                                                                     @RequestBody PlanActiveRequest request) {
        return ok(service.setPlanActive(id, request.isActive()));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> ok(T data) {
        return ResponseEntity.ok(ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build());
    }
}
