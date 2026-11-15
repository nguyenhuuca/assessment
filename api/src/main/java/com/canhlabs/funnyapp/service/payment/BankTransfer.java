package com.canhlabs.funnyapp.service.payment;

import com.canhlabs.funnyapp.enums.TransferDirection;

import java.time.Instant;

/**
 * One bank transfer reported by a payment provider, in a vendor-neutral shape (ADR-0038).
 *
 * @param provider      provider id ({@link PaymentProvider#id()})
 * @param providerTxId  the provider's own transaction id (numeric or UUID), unique per provider
 * @param amount        VND
 * @param code          payment code the provider already extracted from the memo, or null
 * @param content       raw transfer memo
 * @param bank          bank brand / gateway name
 * @param accountNumber raw account number; masked before it is stored
 * @param referenceCode bank reference, used as a second dedupe key
 */
public record BankTransfer(String provider, String providerTxId, TransferDirection direction, long amount,
                           String code, String content, String bank, String accountNumber,
                           String referenceCode, Instant transactionDate) {
}
