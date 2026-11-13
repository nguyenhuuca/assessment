package com.canhlabs.funnyapp.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MarkPaidRequest {
    /** VND the admin saw on the bank statement. */
    private long amount;
}
