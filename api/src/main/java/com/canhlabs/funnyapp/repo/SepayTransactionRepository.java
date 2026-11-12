package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.SepayTransaction;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SepayTransactionRepository extends JpaRepository<SepayTransaction, Long> {

    /**
     * Idempotent insert keyed on sepay_id. Returns 1 when stored, 0 when SePay already reported it
     * (webhook retry or reconcile overlap). Enum arguments are passed as their names.
     * No clearAutomatically: the caller holds the locked PaymentOrder in the same persistence
     * context and updates it after this insert; clearing would detach it and drop that update.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO sepay_transactions
                (sepay_id, order_id, match_status, source, transfer_type, amount, code, content,
                 gateway, account_number_masked, reference_code, transaction_date)
            VALUES
                (:sepayId, :orderId, :matchStatus, :source, :transferType, :amount, :code, :content,
                 :gateway, :accountNumberMasked, :referenceCode, :transactionDate)
            ON CONFLICT (sepay_id) DO NOTHING""", nativeQuery = true)
    int insertIgnore(@Param("sepayId") Long sepayId,
                     @Param("orderId") Long orderId,
                     @Param("matchStatus") String matchStatus,
                     @Param("source") String source,
                     @Param("transferType") String transferType,
                     @Param("amount") long amount,
                     @Param("code") String code,
                     @Param("content") String content,
                     @Param("gateway") String gateway,
                     @Param("accountNumberMasked") String accountNumberMasked,
                     @Param("referenceCode") String referenceCode,
                     @Param("transactionDate") Instant transactionDate);

    Page<SepayTransaction> findByMatchStatusOrderByReceivedAtDesc(TxMatchStatus matchStatus, Pageable pageable);

    List<SepayTransaction> findByOrderIdOrderByReceivedAtAsc(Long orderId);

    long countByMatchStatus(TxMatchStatus matchStatus);
}
