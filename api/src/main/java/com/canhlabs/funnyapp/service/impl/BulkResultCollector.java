package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.admin.BulkSkip;
import com.canhlabs.funnyapp.exception.CustomException;

import java.util.ArrayList;
import java.util.List;

/** Accumulates per-row outcomes of a bulk action into a {@link BulkResultDto}. Not thread-safe. */
public class BulkResultCollector {
    public static final String UNEXPECTED_CODE = "error.unexpected";
    private static final String FALLBACK_CODE = "error.request";

    private int updated;
    private int unchanged;
    private final List<BulkSkip> skipped = new ArrayList<>();
    private final List<String> notFound = new ArrayList<>();

    public void updated() {
        updated++;
    }

    public void unchanged() {
        unchanged++;
    }

    public void notFound(Object id) {
        notFound.add(String.valueOf(id));
    }

    public void skipped(Object id, String code, String message) {
        skipped.add(BulkSkip.builder().id(String.valueOf(id)).code(code).message(message).build());
    }

    /** Skips with the exception's i18n key as code (falls back to a generic code) and its message. */
    public void skipped(Object id, CustomException e) {
        skipped(id, e.getMessageKey() != null ? e.getMessageKey() : FALLBACK_CODE, e.getMessage());
    }

    public BulkResultDto build(int requested) {
        return BulkResultDto.builder()
                .requested(requested)
                .updated(updated)
                .unchanged(unchanged)
                .skipped(List.copyOf(skipped))
                .notFound(List.copyOf(notFound))
                .build();
    }
}
