package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lombok builders ignore field initialisers unless @Builder.Default; these columns are NOT NULL. */
class PaymentEntityDefaultsTest {

    @Test
    void paymentOrder_builderAppliesDefaults() {
        PaymentOrder order = PaymentOrder.builder().code("FM1").type(PaymentType.DONATION).build();

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(order.getPaidAmount()).isZero();
        assertThat(order.getExpectedAmount()).isNull();
        assertThat(order.getUserId()).isNull();
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    void paymentOrder_noArgConstructorHasSameDefaults() {
        PaymentOrder order = new PaymentOrder();

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(order.getPaidAmount()).isZero();
    }

    @Test
    void premiumPlan_builderAppliesDefaults() {
        PremiumPlan plan = PremiumPlan.builder().code("P").name("Plan").durationDays(30).priceVnd(49000L).build();

        assertThat(plan.isActive()).isFalse();
        assertThat(plan.getSortOrder()).isZero();
    }

    @Test
    void premiumPlan_noArgConstructorHasSameDefaults() {
        PremiumPlan plan = new PremiumPlan();

        assertThat(plan.isActive()).isFalse();
        assertThat(plan.getSortOrder()).isZero();
    }
}
