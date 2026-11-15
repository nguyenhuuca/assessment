package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.PaymentTransaction;
import com.canhlabs.funnyapp.enums.TxMatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /**
     * Idempotent insert keyed on (provider, provider_tx_id). Returns 1 when stored, 0 when the provider already
     * reported it (webhook retry or reconcile overlap). Enum arguments are passed as their names.
     * No clearAutomatically: the caller holds the locked PaymentOrder in the same persistence
     * context and updates it after this insert; clearing would detach it and drop that update.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO payment_transactions
                (provider, provider_tx_id, order_id, match_status, source, transfer_type, amount, code, content,
                 gateway, account_number_masked, reference_code, transaction_date)
            VALUES
                (:provider, :providerTxId, :orderId, :matchStatus, :source, :transferType, :amount, :code, :content,
                 :gateway, :accountNumberMasked, :referenceCode, :transactionDate)
            ON CONFLICT (provider, provider_tx_id) DO NOTHING""", nativeQuery = true)
    int insertIgnore(@Param("provider") String provider,
                     @Param("providerTxId") String providerTxId,
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

    Page<PaymentTransaction> findByMatchStatusOrderByReceivedAtDesc(TxMatchStatus matchStatus, Pageable pageable);

    long countByMatchStatus(TxMatchStatus matchStatus);

    /**
     * Same bank transfer already stored under another transaction id (webhook id vs pull-API id, or a second
     * provider watching the same account), matched on bank reference + amount.
     */
    @Query("""
            SELECT COUNT(t) > 0 FROM PaymentTransaction t
             WHERE t.referenceCode = :referenceCode AND t.amount = :amount
               AND NOT (t.provider = :provider AND t.providerTxId = :providerTxId)""")
    boolean existsSameTransferUnderOtherId(@Param("referenceCode") String referenceCode,
                                           @Param("amount") long amount,
                                           @Param("provider") String provider,
                                           @Param("providerTxId") String providerTxId);

    /** Which of these provider ids are stored already. */
    @Query("SELECT t.providerTxId FROM PaymentTransaction t WHERE t.provider = :provider AND t.providerTxId IN :ids")
    Set<String> findStoredTxIds(@Param("provider") String provider, @Param("ids") Collection<String> ids);

    /** Atomic status change guarded by the current status: 1 when changed, 0 when the row moved on or is missing. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PaymentTransaction t SET t.matchStatus = :to WHERE t.id = :id AND t.matchStatus = :from")
    int updateMatchStatus(@Param("id") long id,
                          @Param("from") TxMatchStatus from,
                          @Param("to") TxMatchStatus to);
}
