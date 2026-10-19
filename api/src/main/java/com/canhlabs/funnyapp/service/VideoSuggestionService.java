package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import org.springframework.data.domain.Page;

public interface VideoSuggestionService {

    /** 400 invalidUrl, 429 dailyLimit, 409 duplicate. */
    SuggestionDto submit(Long userId, String url, String title, String note);

    /** The user's own suggestions, newest first; size is clamped to 1..50. */
    Page<SuggestionDto> mine(Long userId, SuggestionFilter filter, int page, int size);

    /** Pending first, then newest; includes suggester and import job id. */
    Page<SuggestionDto> list(SuggestionFilter filter, int page, int size);

    long pendingCount();

    /** Creates exactly one import job (scheduled now) and links it. 503 when import is disabled. */
    SuggestionDto approve(Long id, String titleOverride, Long adminId);

    /**
     * REJECTED, or APPROVED with a FAILED/CANCELLED import job, back to PENDING (decision cleared).
     * 404 notFound, 409 alreadyPending / cannotReopen / duplicate.
     */
    SuggestionDto reopen(Long id, Long adminId);

    /**
     * Owner asks for a re-review of a REJECTED suggestion (max 2 times). A non-blank message replaces the note.
     * 404 notFound (also for other users' ids), 409 notRejected / rereviewLimit / duplicate.
     */
    SuggestionDto requestRereview(Long id, Long userId, String message);

    SuggestionDto reject(Long id, SuggestionRejectReason reason, String note, Long adminId);
}
