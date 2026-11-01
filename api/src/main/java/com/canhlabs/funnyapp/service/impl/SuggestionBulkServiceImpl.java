package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionBulkRequest;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.SuggestionBulkService;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bulk orchestration only (ADR-0036): not transactional, each row calls the single-row
 * {@link VideoSuggestionService} method, so its locking, events and after-commit worker wake-up apply
 * per row and a failing row never affects the others.
 *
 * <p>The single-row service reports a missing suggestion as a {@link CustomException} (404, 4042) rather than
 * a {@link ResourceNotFoundException}. To keep the single-row code untouched, the bulk row maps that one key
 * to {@code notFound} here.
 */
@Service
public class SuggestionBulkServiceImpl implements SuggestionBulkService {
    static final String KEY_NOTE_REQUIRED = VideoSuggestionServiceImpl.KEY_NOTE_REQUIRED;
    static final String KEY_IMPORT_DISABLED = VideoSuggestionServiceImpl.KEY_IMPORT_DISABLED;
    static final String KEY_NOT_FOUND = VideoSuggestionServiceImpl.KEY_NOT_FOUND;

    private VideoSuggestionService suggestionService;
    private AppProperties appProps;

    @Autowired
    public void injectDependencies(VideoSuggestionService suggestionService, AppProperties appProps) {
        this.suggestionService = suggestionService;
        this.appProps = appProps;
    }

    @Override
    public BulkResultDto bulk(SuggestionBulkRequest request, Long adminId) {
        validateOnce(request);
        List<Long> ids = request.distinctIds();
        BulkResultCollector collector = new BulkResultCollector("suggestions:" + request.getAction())
                .notFoundWhen(e -> KEY_NOT_FOUND.equals(e.getMessageKey()));
        for (Long id : ids) {
            collector.run(id, () -> applyRow(request, id, adminId));
        }
        return collector.build(ids.size());
    }

    private void validateOnce(SuggestionBulkRequest request) {
        if (request.getAction() == SuggestionBulkRequest.Action.APPROVE) {
            if (!appProps.getVideoImport().isEnabled()) {
                throw CustomException.of(HttpStatus.SERVICE_UNAVAILABLE, 4100, KEY_IMPORT_DISABLED);
            }
            return;
        }
        String note = request.getNote();
        if (request.getReason() == SuggestionRejectReason.OTHER && (note == null || note.isBlank())) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 4001, KEY_NOTE_REQUIRED);
        }
    }

    private boolean applyRow(SuggestionBulkRequest request, Long id, Long adminId) {
        if (request.getAction() == SuggestionBulkRequest.Action.APPROVE) {
            suggestionService.approve(id, null, adminId);
        } else {
            suggestionService.reject(id, request.getReason(), request.getNote(), adminId);
        }
        return true;
    }
}
