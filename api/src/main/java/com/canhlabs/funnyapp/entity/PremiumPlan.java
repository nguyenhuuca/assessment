package com.canhlabs.funnyapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** An admin-editable Premium plan: price in VND and how many days of access it grants. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "premium_plans")
public class PremiumPlan extends BaseDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "active", nullable = false)
    @Builder.Default // builder ignores field initialisers
    private boolean active = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
