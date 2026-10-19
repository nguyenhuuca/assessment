package com.canhlabs.funnyapp.enums;

import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Set;

/**
 * The one table of which suggestion may move by which transition, and the refusal when it may not.
 * Pure rules: no repository, no entity. Race guards (row locks, guarded UPDATE, unique index) stay in the service.
 */
public enum SuggestionTransition {
    /** Admin accepts a pending suggestion. */
    APPROVE {
        @Override
        public Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount) {
            return requirePending(status);
        }
    },
    /** Admin rejects a pending suggestion. */
    REJECT {
        @Override
        public Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount) {
            return requirePending(status);
        }
    },
    /** Admin puts a rejected suggestion, or an approved one whose import ended badly, back to pending. */
    REOPEN {
        @Override
        public Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount) {
            if (status == SuggestionStatus.PENDING) {
                return Optional.of(ALREADY_PENDING);
            }
            if (status == SuggestionStatus.APPROVED
                    && (jobStatus == null || !REOPENABLE_JOB_STATUSES.contains(jobStatus))) {
                return Optional.of(CANNOT_REOPEN);
            }
            return Optional.empty();
        }
    },
    /** The suggester asks for a rejected suggestion to be looked at again. */
    REREVIEW {
        @Override
        public Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount) {
            if (status != SuggestionStatus.REJECTED) {
                return Optional.of(NOT_REJECTED);
            }
            if (rereviewCount >= MAX_REREVIEWS) {
                return Optional.of(REREVIEW_LIMIT);
            }
            return Optional.empty();
        }
    };

    /** User re-review requests allowed per suggestion. */
    public static final int MAX_REREVIEWS = 2;
    /** An approved suggestion can be reopened only when its import job ended in one of these. */
    public static final Set<ImportStatus> REOPENABLE_JOB_STATUSES = Set.of(ImportStatus.FAILED, ImportStatus.CANCELLED);

    private static final Refusal NOT_PENDING = new Refusal(HttpStatus.CONFLICT, 4093, "error.suggestion.notPending");
    private static final Refusal ALREADY_PENDING =
            new Refusal(HttpStatus.CONFLICT, 4095, "error.suggestion.alreadyPending");
    private static final Refusal CANNOT_REOPEN =
            new Refusal(HttpStatus.CONFLICT, 4096, "error.suggestion.cannotReopen");
    private static final Refusal NOT_REJECTED =
            new Refusal(HttpStatus.CONFLICT, 4097, "error.suggestion.notRejected");
    private static final Refusal REREVIEW_LIMIT =
            new Refusal(HttpStatus.CONFLICT, 4098, "error.suggestion.rereviewLimit");

    /** Why a transition is refused: the HTTP status, sub code and message key of the error. */
    public record Refusal(HttpStatus status, int subCode, String messageKey) {
    }

    /**
     * @param status        current suggestion status
     * @param jobStatus     status of the linked import job; only read for REOPEN of an APPROVED suggestion
     * @param rereviewCount re-review requests already made; only read for REREVIEW
     * @return the refusal, or empty when the transition is allowed
     */
    public abstract Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount);

    public boolean allowed(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount) {
        return check(status, jobStatus, rereviewCount).isEmpty();
    }

    private static Optional<Refusal> requirePending(SuggestionStatus status) {
        return status == SuggestionStatus.PENDING ? Optional.empty() : Optional.of(NOT_PENDING);
    }
}
