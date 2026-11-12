package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.config.JsonMappers;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.event.PaymentEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentNotificationListenerTest {

    @Mock PaymentOrderRepository orderRepository;
    @Mock UserRepo userRepo;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPublisher publisher;

    PaymentNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentNotificationListener(orderRepository, userRepo, notificationRepository, publisher,
                JsonMappers.create());
    }

    private static User admin(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private void order(PaymentType type, String donor) {
        PaymentOrder o = PaymentOrder.builder().id(5L).code("FM1234").type(type).paidAmount(50000L)
                .donorName(donor).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(userRepo.findActiveAdmins()).thenReturn(List.of(admin(1L), admin(2L)));
    }

    private void expectType(PaymentType orderType, PaymentOrderStatus status, String notifType) {
        order(orderType, "Lan");
        listener.onPayment(new PaymentEvent(5L, orderType, status, 30000L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(1L), eq(notifType), eq("payment:5"),
                isNull(), isNull(), isNull(), payload.capture());
        verify(notificationRepository).upsertGrouped(eq(2L), eq(notifType), eq("payment:5"),
                isNull(), isNull(), isNull(), anyString());
        assertThat(payload.getValue()).contains("\"orderCode\":\"FM1234\"").contains("\"amount\":30000")
                .contains("\"paidAmount\":50000").contains("\"donorName\":\"Lan\"");
        verify(publisher).unreadChanged(1L);
        verify(publisher).unreadChanged(2L);
    }

    @Test
    void donationPaid_mapsToDonationReceived() {
        expectType(PaymentType.DONATION, PaymentOrderStatus.PAID, "DONATION_RECEIVED");
    }

    @Test
    void premiumPaid_mapsToPremiumPurchased() {
        expectType(PaymentType.PREMIUM, PaymentOrderStatus.PAID, "PREMIUM_PURCHASED");
    }

    @Test
    void underpaid_mapsToPaymentUnderpaid() {
        expectType(PaymentType.DONATION, PaymentOrderStatus.UNDERPAID, "PAYMENT_UNDERPAID");
    }

    @Test
    void anonymousDonor_payloadHasNoDonorName() {
        order(PaymentType.DONATION, null);
        listener.onPayment(new PaymentEvent(5L, PaymentType.DONATION, PaymentOrderStatus.PAID, 50000L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(1L), anyString(), anyString(), any(), any(), any(),
                payload.capture());
        assertThat(payload.getValue()).doesNotContain("donorName");
    }

    @Test
    void otherStatus_isIgnored() {
        order(PaymentType.DONATION, null);
        listener.onPayment(new PaymentEvent(5L, PaymentType.DONATION, PaymentOrderStatus.EXPIRED, 0L));
        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void unknownOrder_isNoOp() {
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());
        listener.onPayment(new PaymentEvent(9L, PaymentType.DONATION, PaymentOrderStatus.PAID, 1L));
        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void lookupFailure_neverEscapes() {
        when(orderRepository.findById(anyLong())).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> listener.onPayment(new PaymentEvent(5L, PaymentType.DONATION, PaymentOrderStatus.PAID, 1L)))
                .doesNotThrowAnyException();
        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void insertFailureForOneAdmin_skipsItsSse_andContinues() {
        order(PaymentType.DONATION, "Lan");
        when(notificationRepository.upsertGrouped(eq(1L), anyString(), anyString(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("insert failed"));
        assertThatCode(() -> listener.onPayment(new PaymentEvent(5L, PaymentType.DONATION, PaymentOrderStatus.PAID, 1L)))
                .doesNotThrowAnyException();
        verify(publisher, never()).unreadChanged(1L);
        verify(publisher).unreadChanged(2L);
    }
}
