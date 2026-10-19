package com.canhlabs.funnyapp.enums;

import com.canhlabs.funnyapp.enums.SuggestionTransition.Refusal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.stream.Stream;

import static com.canhlabs.funnyapp.enums.SuggestionStatus.APPROVED;
import static com.canhlabs.funnyapp.enums.SuggestionStatus.PENDING;
import static com.canhlabs.funnyapp.enums.SuggestionStatus.REJECTED;
import static com.canhlabs.funnyapp.enums.SuggestionTransition.APPROVE;
import static com.canhlabs.funnyapp.enums.SuggestionTransition.REJECT;
import static com.canhlabs.funnyapp.enums.SuggestionTransition.REOPEN;
import static com.canhlabs.funnyapp.enums.SuggestionTransition.REREVIEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionTransitionTest {

    private static final Refusal NOT_PENDING = refusal(4093, "error.suggestion.notPending");
    private static final Refusal ALREADY_PENDING = refusal(4095, "error.suggestion.alreadyPending");
    private static final Refusal CANNOT_REOPEN = refusal(4096, "error.suggestion.cannotReopen");
    private static final Refusal NOT_REJECTED = refusal(4097, "error.suggestion.notRejected");
    private static final Refusal REREVIEW_LIMIT = refusal(4098, "error.suggestion.rereviewLimit");

    private static Refusal refusal(int subCode, String key) {
        return new Refusal(HttpStatus.CONFLICT, subCode, key);
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(APPROVE, PENDING, null, 0, Optional.empty()),
                Arguments.of(APPROVE, APPROVED, null, 0, Optional.of(NOT_PENDING)),
                Arguments.of(APPROVE, REJECTED, null, 0, Optional.of(NOT_PENDING)),
                Arguments.of(REJECT, PENDING, null, 0, Optional.empty()),
                Arguments.of(REJECT, APPROVED, null, 0, Optional.of(NOT_PENDING)),
                Arguments.of(REJECT, REJECTED, null, 0, Optional.of(NOT_PENDING)),
                Arguments.of(REOPEN, PENDING, null, 0, Optional.of(ALREADY_PENDING)),
                Arguments.of(REOPEN, REJECTED, null, 0, Optional.empty()),
                Arguments.of(REOPEN, REJECTED, ImportStatus.DONE, 0, Optional.empty()),
                Arguments.of(REREVIEW, PENDING, null, 0, Optional.of(NOT_REJECTED)),
                Arguments.of(REREVIEW, APPROVED, null, 0, Optional.of(NOT_REJECTED)),
                Arguments.of(REREVIEW, REJECTED, null, 0, Optional.empty()),
                Arguments.of(REREVIEW, REJECTED, null, 1, Optional.empty()),
                Arguments.of(REREVIEW, REJECTED, null, 2, Optional.of(REREVIEW_LIMIT)),
                Arguments.of(REREVIEW, REJECTED, null, 3, Optional.of(REREVIEW_LIMIT)));
    }

    @ParameterizedTest
    @MethodSource("table")
    void check_returnsExpectedRefusal(SuggestionTransition transition, SuggestionStatus status,
                                      ImportStatus jobStatus, int rereviewCount, Optional<Refusal> expected) {
        assertEquals(expected, transition.check(status, jobStatus, rereviewCount));
        assertEquals(expected.isEmpty(), transition.allowed(status, jobStatus, rereviewCount));
    }

    @Test
    void reopen_approvedWithoutJobStatus_isRefused() {
        assertEquals(Optional.of(CANNOT_REOPEN), REOPEN.check(APPROVED, null, 0));
        assertFalse(REOPEN.allowed(APPROVED, null, 0));
    }

    @ParameterizedTest
    @EnumSource(ImportStatus.class)
    void reopen_approved_dependsOnImportStatus(ImportStatus jobStatus) {
        boolean reopenable = jobStatus == ImportStatus.FAILED || jobStatus == ImportStatus.CANCELLED;
        assertEquals(reopenable ? Optional.empty() : Optional.of(CANNOT_REOPEN), REOPEN.check(APPROVED, jobStatus, 0));
        assertEquals(reopenable, REOPEN.allowed(APPROVED, jobStatus, 0));
    }

    @ParameterizedTest
    @EnumSource(ImportStatus.class)
    void reopen_pendingRefusedBeforeJobStatus(ImportStatus jobStatus) {
        assertEquals(Optional.of(ALREADY_PENDING), REOPEN.check(PENDING, jobStatus, 0));
    }

    @Test
    void constants_matchCurrentRules() {
        assertEquals(2, SuggestionTransition.MAX_REREVIEWS);
        assertTrue(SuggestionTransition.REOPENABLE_JOB_STATUSES
                .containsAll(java.util.List.of(ImportStatus.FAILED, ImportStatus.CANCELLED)));
        assertEquals(2, SuggestionTransition.REOPENABLE_JOB_STATUSES.size());
    }
}
