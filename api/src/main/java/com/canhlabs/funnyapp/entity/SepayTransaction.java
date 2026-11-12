package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.TxMatchStatus;
import com.canhlabs.funnyapp.enums.TxSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/** A transfer reported by SePay. The SePay id is the primary key, which makes ingestion idempotent. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "sepay_transactions")
public class SepayTransaction {
    @Id
    @Column(name = "sepay_id")
    private Long sepayId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 16)
    private TxMatchStatus matchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TxSource source;

    @Column(name = "transfer_type", nullable = false, length = 8)
    private String transferType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "gateway", length = 64)
    private String gateway;

    @Column(name = "account_number_masked", length = 32)
    private String accountNumberMasked;

    @Column(name = "reference_code", length = 64)
    private String referenceCode;

    @Column(name = "transaction_date")
    private Instant transactionDate;

    /** Set by the database default. */
    @Column(name = "received_at", insertable = false, updatable = false)
    private Instant receivedAt;
}
