package com.canhlabs.funnyapp.service.payment;

/** What the payer sees for one order: a QR image URL plus the bank fields to type by hand. */
public record PaymentInstructions(String qrUrl, String bankCode, String bankAccount, String accountName) {
}
