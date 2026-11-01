package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.admin.BulkSkip;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Accumulates per-row outcomes of a bulk action into a {@link BulkResultDto} (ADR-0036). Not thread-safe:
 * one instance per request, rows applied sequentially.
 *
 * <p>{@link #run} owns the exception → outcome mapping for every bulk service. Only {@link CustomException}
 * messages reach the client (they are user-facing by design, as in {@code RestExceptionHandler}); any other
 * failure gets a fixed message and the real text is logged server-side only (CWE-209).
 */
@Slf4j
public class BulkResultCollector {
    public static final String UNEXPECTED_CODE = "error.unexpected";
    public static final String CONFLICT_CODE = "error.conflict";
    static final String FALLBACK_CODE = "error.request";
    static final String UNEXPECTED_MESSAGE = "Unexpected error";
    static final String CONFLICT_MESSAGE = "Conflicts with the current state";

    private final String action;
    private int updated;
    private int unchanged;
    private final List<BulkSkip> skipped = new ArrayList<>();
    private final List<String> notFound = new ArrayList<>();

    /** @param action label for server-side logs, e.g. {@code "videos:DELETE"} */
    public BulkResultCollector(String action) {
        this.action = action;
    }

    /**
     * Applies one row. {@code row} returns {@code true} when it changed something, {@code false} for a no-op.
     * A failing row is recorded and never stops the batch.
     */
    public void run(Object id, BooleanSupplier row) {
        try {
            if (row.getAsBoolean()) {
                updated++;
            } else {
                unchanged++;
            }
        } catch (ResourceNotFoundException e) {
            notFound.add(String.valueOf(id));
        } catch (CustomException e) {
            skip(id, e.getMessageKey() != null ? e.getMessageKey() : FALLBACK_CODE, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Bulk {} conflict for id {}: {}", action, id, e.getMessage());
            skip(id, CONFLICT_CODE, CONFLICT_MESSAGE);
        } catch (RuntimeException e) {
            log.warn("Bulk {} failed for id {}: {}: {}", action, id, e.getClass().getSimpleName(), e.getMessage());
            skip(id, UNEXPECTED_CODE, UNEXPECTED_MESSAGE);
        }
    }

    private void skip(Object id, String code, String message) {
        skipped.add(BulkSkip.builder().id(String.valueOf(id)).code(code).message(message).build());
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
