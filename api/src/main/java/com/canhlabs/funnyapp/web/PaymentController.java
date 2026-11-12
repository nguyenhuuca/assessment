package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.payment.CreateOrderRequest;
import com.canhlabs.funnyapp.dto.payment.OrderDto;
import com.canhlabs.funnyapp.dto.payment.OrderStatusDto;
import com.canhlabs.funnyapp.dto.payment.PlanDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.PaymentOrderService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Donations and Premium purchases via bank transfer (ADR-0037). Public; a JWT is optional on order creation. */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/payments")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    public PaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @Operation(summary = "Payment config", description = "Public. {enabled} is false while SePay is not configured, so the UI can hide donate.")
    @GetMapping("/config")
    public ResponseEntity<ResultObjectInfo<Map<String, Boolean>>> config() {
        return ResponseEntity.ok(ResultObjectInfo.<Map<String, Boolean>>builder()
                .status(ResultStatus.SUCCESS)
                .data(Map.of("enabled", paymentOrderService.isOrderingEnabled()))
                .build());
    }

    @Operation(summary = "List active Premium plans", description = "Public.")
    @GetMapping("/plans")
    public ResponseEntity<ResultListInfo<PlanDto>> plans() {
        return ResponseEntity.ok(ResultListInfo.<PlanDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(paymentOrderService.listActivePlans())
                .build());
    }

    @Operation(summary = "Create a payment order",
            description = "DONATION is open to guests; PREMIUM needs a logged-in user. Returns the QR and transfer details.")
    @PostMapping("/orders")
    @AuditLog("createPaymentOrder")
    @RateLimited(permit = 10)
    public ResponseEntity<ResultObjectInfo<OrderDto>> createOrder(@RequestBody CreateOrderRequest request) {
        UserDetailDto user = AppUtils.getCurrentUser();
        Long userId = user == null ? null : user.getId();
        return ResponseEntity.ok(ResultObjectInfo.<OrderDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(paymentOrderService.createOrder(request, userId))
                .build());
    }

    @Operation(summary = "Order status", description = "Public, polled by the QR screen. Status, type, amount only.")
    @GetMapping("/orders/{code}")
    public ResponseEntity<ResultObjectInfo<OrderStatusDto>> order(@PathVariable String code) {
        return ResponseEntity.ok(ResultObjectInfo.<OrderStatusDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(paymentOrderService.getByCode(code))
                .build());
    }
}
