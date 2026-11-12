package com.canhlabs.funnyapp.repo;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the purge query text; the project has no database test harness. */
class PaymentOrderRepositoryQueryTest {

    @Test
    void purgeUnpaidExpired_deletesOnlyExpiredUnpaidUnlinkedOldOrders() throws Exception {
        Method m = PaymentOrderRepository.class.getMethod("purgeUnpaidExpired", Instant.class);
        Query q = m.getAnnotation(Query.class);

        assertThat(m.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(q.value()).startsWith("delete from PaymentOrder o")
                .contains("PaymentOrderStatus.EXPIRED")
                .contains("o.paidAmount = 0")
                .contains("o.createdAt < :cutoff")
                .contains("not exists (select 1 from SepayTransaction t where t.orderId = o.id)");
    }
}
