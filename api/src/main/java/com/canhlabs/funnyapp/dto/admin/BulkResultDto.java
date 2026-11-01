package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Outcome of a per-row bulk action (ADR-0036): every requested id lands in exactly one bucket. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkResultDto {
    /** Distinct ids received. */
    private int requested;
    /** Rows that actually changed. */
    private int updated;
    /** Rows already in the target state. */
    private int unchanged;
    private List<BulkSkip> skipped;
    private List<String> notFound;
}
