package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionBulkRequest;

public interface SuggestionBulkService {
    /**
     * Approves or rejects each distinct id on its own as {@code adminId}; one failing row never stops the rest.
     * Request-level problems (missing note, import disabled) fail the whole request before any row.
     */
    BulkResultDto bulk(SuggestionBulkRequest request, Long adminId);
}
