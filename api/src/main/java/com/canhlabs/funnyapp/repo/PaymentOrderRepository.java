package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.PaymentOrder;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.code = :code")
    Optional<PaymentOrder> findByCodeForUpdate(@Param("code") String code);

    /** Admin list across all types, newest first. */
    @Query("select o from PaymentOrder o where o.status in :statuses order by o.createdAt desc, o.id desc")
    Page<PaymentOrder> search(@Param("statuses") Collection<PaymentOrderStatus> statuses, Pageable pageable);

    /** Admin list for one type, newest first. */
    @Query("""
            select o from PaymentOrder o
             where o.type = :type and o.status in :statuses
             order by o.createdAt desc, o.id desc""")
    Page<PaymentOrder> searchByType(@Param("type") PaymentType type,
                                    @Param("statuses") Collection<PaymentOrderStatus> statuses,
                                    Pageable pageable);

    /** Lifetime money received for a type (unpaid orders hold 0). */
    @Query("select coalesce(sum(o.paidAmount), 0) from PaymentOrder o where o.type = :type")
    long sumPaid(@Param("type") PaymentType type);

    /** Money received for a type on orders created at or after {@code from}. */
    @Query("select coalesce(sum(o.paidAmount), 0) from PaymentOrder o where o.type = :type and o.createdAt >= :from")
    long sumPaidSince(@Param("type") PaymentType type, @Param("from") Instant from);

    /** Flip overdue PENDING orders to EXPIRED. Returns the number of rows changed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentOrder o
               set o.status = com.canhlabs.funnyapp.enums.PaymentOrderStatus.EXPIRED
             where o.status = com.canhlabs.funnyapp.enums.PaymentOrderStatus.PENDING
               and o.expiresAt < :now""")
    int expirePending(@Param("now") Instant now);
}
