package com.canhlabs.funnyapp.dto.payment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPaymentSummaryDto {
    private long donationTotal;
    private long donation30d;
    private long premiumTotal;
    private long premium30d;
    private long pendingOrders;
    private long underpaidOrders;
    private long unmatchedTransactions;
}
