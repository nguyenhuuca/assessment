package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.payment.CreateOrderRequest;
import com.canhlabs.funnyapp.dto.payment.OrderDto;
import com.canhlabs.funnyapp.dto.payment.OrderStatusDto;
import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.entity.PremiumPlan;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.PaymentOrderRepository;
import com.canhlabs.funnyapp.repo.PremiumPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceImplTest {

    @Mock PaymentOrderRepository orderRepo;
    @Mock PremiumPlanRepository planRepo;

    AppProperties props;
    PaymentOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        AppProperties.Sepay s = props.getSepay();
        s.setBankAccount("123456");
        s.setBankCode("MB");
        s.setAccountName("FUNNY");
        service = new PaymentOrderServiceImpl(orderRepo, planRepo, props);
    }

    private void stubSave() {
        when(orderRepo.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateOrderRequest donation(Long amount) {
        return CreateOrderRequest.builder().type(PaymentType.DONATION).amount(amount).build();
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Test
    void isOrderingEnabled_followsBankConfig() {
        assertThat(service.isOrderingEnabled()).isTrue();
        props.getSepay().setBankAccount("");
        assertThat(service.isOrderingEnabled()).isFalse();
    }

    @Test
    void donation_guest_createsOrderWithFormattedCodeAndQr() {
        stubSave();
        CreateOrderRequest req = donation(50000L);
        req.setDonorName("  Alice  ");
        req.setMessage("  hi ");

        OrderDto dto = service.createOrder(req, null);

        ArgumentCaptor<PaymentOrder> cap = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderRepo).save(cap.capture());
        PaymentOrder saved = cap.getValue();
        assertThat(saved.getCode()).matches("FM[A-Z0-9]{8}");
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getExpectedAmount()).isNull();
        assertThat(saved.getDonorName()).isEqualTo("Alice");
        assertThat(saved.getMessage()).isEqualTo("hi");
        assertThat(saved.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(Duration.between(Instant.now(), saved.getExpiresAt()).toHours()).isBetween(22L, 24L);
        assertThat(dto.getAmount()).isEqualTo(50000L);
        assertThat(dto.getMemo()).isEqualTo(saved.getCode());
        assertThat(dto.getQrUrl()).contains("acc=123456", "bank=MB", "amount=50000", "des=" + saved.getCode());
        assertThat(dto.getAccountName()).isEqualTo("FUNNY");
    }

    @Test
    void donation_blankNameAndMessage_becomeNull() {
        stubSave();
        CreateOrderRequest req = donation(20000L);
        req.setDonorName("   ");
        req.setMessage("");

        service.createOrder(req, 5L);

        ArgumentCaptor<PaymentOrder> cap = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderRepo).save(cap.capture());
        assertThat(cap.getValue().getDonorName()).isNull();
        assertThat(cap.getValue().getMessage()).isNull();
        assertThat(cap.getValue().getUserId()).isEqualTo(5L);
    }

    @Test
    void code_retriesOnCollision() {
        stubSave();
        when(orderRepo.existsByCode(anyString())).thenReturn(true, true, false);

        OrderDto dto = service.createOrder(donation(20000L), null);

        assertThat(dto.getCode()).matches("FM[A-Z0-9]{8}");
        verify(orderRepo, org.mockito.Mockito.times(3)).existsByCode(anyString());
    }

    @Test
    void code_givesUpAfterMaxAttempts() {
        when(orderRepo.existsByCode(anyString())).thenReturn(true);

        assertStatus(() -> service.createOrder(donation(20000L), null), HttpStatus.INTERNAL_SERVER_ERROR);
        verify(orderRepo, org.mockito.Mockito.times(PaymentOrderServiceImpl.MAX_CODE_ATTEMPTS)).existsByCode(anyString());
        verify(orderRepo, never()).save(any());
    }

    @Test
    void donation_belowMinimumOrMissing_isBadRequest() {
        assertStatus(() -> service.createOrder(donation(9999L), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.createOrder(donation(null), null), HttpStatus.BAD_REQUEST);
        verify(orderRepo, never()).save(any());
    }

    @Test
    void donation_nameAndMessageLimits() {
        CreateOrderRequest longName = donation(20000L);
        longName.setDonorName("x".repeat(PaymentOrderServiceImpl.MAX_NAME + 1));
        assertStatus(() -> service.createOrder(longName, null), HttpStatus.BAD_REQUEST);

        CreateOrderRequest longMsg = donation(20000L);
        longMsg.setMessage("x".repeat(PaymentOrderServiceImpl.MAX_MESSAGE + 1));
        assertStatus(() -> service.createOrder(longMsg, null), HttpStatus.BAD_REQUEST);

        stubSave();
        CreateOrderRequest ok = donation(20000L);
        ok.setDonorName("x".repeat(PaymentOrderServiceImpl.MAX_NAME));
        ok.setMessage("y".repeat(PaymentOrderServiceImpl.MAX_MESSAGE));
        assertThat(service.createOrder(ok, null).getCode()).isNotNull();
    }

    @Test
    void notConfigured_is503() {
        props.getSepay().setBankAccount("");

        assertStatus(() -> service.createOrder(donation(20000L), null), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingType_isBadRequest() {
        assertStatus(() -> service.createOrder(new CreateOrderRequest(), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.createOrder(null, null), HttpStatus.BAD_REQUEST);
    }

    private static PremiumPlan plan(boolean active) {
        PremiumPlan p = PremiumPlan.builder().code("PREMIUM_1M").name("1 month").durationDays(30)
                .priceVnd(99000).active(active).build();
        p.setId(3L);
        return p;
    }

    @Test
    void premium_guest_is401() {
        CreateOrderRequest req = CreateOrderRequest.builder().type(PaymentType.PREMIUM).planId(3L).build();

        assertStatus(() -> service.createOrder(req, null), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void premium_activePlan_usesPlanPrice() {
        stubSave();
        when(planRepo.findById(3L)).thenReturn(Optional.of(plan(true)));
        CreateOrderRequest req = CreateOrderRequest.builder().type(PaymentType.PREMIUM).planId(3L).amount(1L).build();

        OrderDto dto = service.createOrder(req, 9L);

        ArgumentCaptor<PaymentOrder> cap = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderRepo).save(cap.capture());
        assertThat(cap.getValue().getExpectedAmount()).isEqualTo(99000L);
        assertThat(cap.getValue().getPlanId()).isEqualTo(3L);
        assertThat(cap.getValue().getUserId()).isEqualTo(9L);
        assertThat(dto.getAmount()).isEqualTo(99000L);
    }

    @Test
    void premium_inactiveUnknownOrMissingPlan_isBadRequest() {
        when(planRepo.findById(3L)).thenReturn(Optional.of(plan(false)));
        when(planRepo.findById(4L)).thenReturn(Optional.empty());

        for (Long id : new Long[]{3L, 4L, null}) {
            CreateOrderRequest req = CreateOrderRequest.builder().type(PaymentType.PREMIUM).planId(id).build();
            assertStatus(() -> service.createOrder(req, 9L), HttpStatus.BAD_REQUEST);
        }
        verify(orderRepo, never()).save(any());
    }

    @Test
    void listActivePlans_mapsFields() {
        when(planRepo.findByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(plan(true)));

        var plans = service.listActivePlans();

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).getCode()).isEqualTo("PREMIUM_1M");
        assertThat(plans.get(0).getPriceVnd()).isEqualTo(99000L);
        assertThat(plans.get(0).getDurationDays()).isEqualTo(30);
    }

    @Test
    void getByCode_unpaidAndPaid() {
        PaymentOrder o = PaymentOrder.builder().code("FMAAAAAAAA").type(PaymentType.PREMIUM)
                .expectedAmount(99000L).expiresAt(Instant.now()).build();
        when(orderRepo.findByCode("FMAAAAAAAA")).thenReturn(Optional.of(o));

        OrderStatusDto unpaid = service.getByCode(" fmaaaaaaaa ");
        assertThat(unpaid.getAmount()).isEqualTo(99000L);
        assertThat(unpaid.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);

        o.setPaidAmount(120000L);
        o.setStatus(PaymentOrderStatus.PAID);
        assertThat(service.getByCode("FMAAAAAAAA").getAmount()).isEqualTo(120000L);
    }

    @Test
    void getByCode_unknownOrNull_is404() {
        when(orderRepo.findByCode(anyString())).thenReturn(Optional.empty());

        assertStatus(() -> service.getByCode("NOPE"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.getByCode(null), HttpStatus.NOT_FOUND);
    }

    @Test
    void expireStaleOrders_returnsRepoCount() {
        when(orderRepo.expirePending(any(Instant.class))).thenReturn(2, 0);

        assertThat(service.expireStaleOrders()).isEqualTo(2);
        assertThat(service.expireStaleOrders()).isZero();
    }
}
