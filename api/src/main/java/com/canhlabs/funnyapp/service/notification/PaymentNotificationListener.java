package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.event.PaymentEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tells every active admin about a donation, a Premium purchase or an underpaid order (PRD FR-6). Runs on the
 * async executor and never lets an exception escape.
 */
@Slf4j
@Component
public class PaymentNotificationListener {

    static final String GROUP_PREFIX = "payment:";

    private final PaymentOrderRepository orderRepository;
    private final UserRepo userRepo;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher publisher;
    private final JsonMapper objectMapper;

    public PaymentNotificationListener(PaymentOrderRepository orderRepository,
                                       UserRepo userRepo,
                                       NotificationRepository notificationRepository,
                                       NotificationPublisher publisher,
                                       JsonMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepo = userRepo;
        this.notificationRepository = notificationRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentEvent event) {
        try {
            Optional<NotificationType> type = mapType(event);
            if (type.isEmpty()) {
                return;
            }
            orderRepository.findById(event.orderId()).ifPresent(order -> notifyAdmins(order, event, type.get()));
        } catch (Exception e) {
            log.error("Failed to create payment notifications for order {}", event.orderId(), e);
        }
    }

    static Optional<NotificationType> mapType(PaymentEvent event) {
        if (event.status() == PaymentOrderStatus.UNDERPAID) {
            return Optional.of(NotificationType.PAYMENT_UNDERPAID);
        }
        if (event.status() == PaymentOrderStatus.PAID) {
            return Optional.of(event.type() == PaymentType.PREMIUM
                    ? NotificationType.PREMIUM_PURCHASED : NotificationType.DONATION_RECEIVED);
        }
        return Optional.empty();
    }

    private void notifyAdmins(PaymentOrder order, PaymentEvent event, NotificationType type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderCode", order.getCode());
        payload.put("amount", event.amount());
        payload.put("paidAmount", order.getPaidAmount());
        payload.put("type", order.getType().name());
        if (order.getDonorName() != null) {
            payload.put("donorName", order.getDonorName());
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payment notification for order {}", order.getId(), e);
            return;
        }
        for (User admin : userRepo.findActiveAdmins()) {
            try {
                notificationRepository.upsertGrouped(admin.getId(), type.name(), GROUP_PREFIX + order.getId(),
                        null, null, null, json);
            } catch (Exception e) {
                log.error("Failed to store payment notification for admin {}", admin.getId(), e);
                continue;
            }
            publisher.unreadChanged(admin.getId());
        }
    }
}
