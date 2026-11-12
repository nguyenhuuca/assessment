package com.canhlabs.funnyapp.dto.payment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPlanDto {
    private Long id;
    private String code;
    private String name;
    private int durationDays;
    private long priceVnd;
    private boolean active;
    private int sortOrder;
}
