package com.canhlabs.funnyapp.dto.payment;

import java.time.Instant;

/**
 * One SePay bank transaction, from the webhook or the reconcile API. {@code transferType} is "in" or "out";
 * {@code amount} is VND. The raw account number is masked before it is stored.
 */
public record SepayTxPayload(long id, String gateway, Instant transactionDate, String accountNumber, String code,
                             String content, String transferType, long amount, String referenceCode) {
}
