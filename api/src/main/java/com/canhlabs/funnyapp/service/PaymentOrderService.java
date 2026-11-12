package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.payment.CreateOrderRequest;
import com.canhlabs.funnyapp.dto.payment.OrderDto;
import com.canhlabs.funnyapp.dto.payment.OrderStatusDto;
import com.canhlabs.funnyapp.dto.payment.PlanDto;

import java.util.List;

public interface PaymentOrderService {

    List<PlanDto> listActivePlans();

    /** @param userId logged-in user id, or null for a guest (guests may only donate). */
    OrderDto createOrder(CreateOrderRequest request, Long userId);

    OrderStatusDto getByCode(String code);

    /** Flips overdue PENDING orders to EXPIRED; returns how many changed. */
    int expireStaleOrders();
}
