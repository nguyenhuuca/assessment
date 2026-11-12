package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimited;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerRateLimitTest {

    @Test
    void orderLookup_isRateLimitedToPollingBudget() throws Exception {
        Method m = PaymentController.class.getMethod("order", String.class);
        RateLimited rl = m.getAnnotation(RateLimited.class);
        assertThat(rl).isNotNull();
        assertThat(rl.permit()).isEqualTo(120);
        assertThat(rl.windowInSeconds()).isEqualTo(60);
    }
}
