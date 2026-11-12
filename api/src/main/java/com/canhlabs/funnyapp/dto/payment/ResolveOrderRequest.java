package com.canhlabs.funnyapp.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResolveOrderRequest {
    private boolean grantPremium;
}
