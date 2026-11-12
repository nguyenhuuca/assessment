package com.canhlabs.funnyapp.dto.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body for creating or replacing a Premium plan. {@code active} defaults to false, {@code sortOrder} to 0. */
@Getter
@Setter
@NoArgsConstructor
public class PlanRequest {
    private String code;
    private String name;
    private Integer durationDays;
    private Long priceVnd;
    private Boolean active;
    private Integer sortOrder;
}
