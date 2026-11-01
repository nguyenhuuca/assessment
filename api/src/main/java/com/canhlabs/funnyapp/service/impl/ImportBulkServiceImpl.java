package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportBulkRequest;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ResourceNotFoundException;
import com.canhlabs.funnyapp.service.ImportBulkService;
import com.canhlabs.funnyapp.service.VideoImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bulk orchestration only (ADR-0036): not transactional, each row calls the single-row
 * {@link VideoImportService} cancel/retry, so its conditional updates, 409 guards and worker side effects
 * (wake-up, cancel request) apply unchanged and a failing row never affects the others.
 *
 * <p>The single-row service reports a missing job and its 409s as {@link CustomException}s whose text is the
 * error key (no {@code messageKey}). They are translated here so single-row HTTP behavior stays untouched:
 * 404 becomes {@link ResourceNotFoundException} (notFound bucket); other un-keyed CustomExceptions are keyed
 * so the collector reports the existing key (INVALID_STATE, DUPLICATE_ACTIVE) as the skip code.
 */
@Service
public class ImportBulkServiceImpl implements ImportBulkService {
    private VideoImportService videoImportService;

    @Autowired
    public void injectVideoImportService(VideoImportService videoImportService) {
        this.videoImportService = videoImportService;
    }

    @Override
    public BulkResultDto bulk(ImportBulkRequest request) {
        List<Long> ids = request.distinctIds();
        BulkResultCollector collector = new BulkResultCollector("imports:" + request.getAction());
        for (Long id : ids) {
            collector.run(id, () -> applyRow(request.getAction(), id));
        }
        return collector.build(ids.size());
    }

    private boolean applyRow(ImportBulkRequest.Action action, Long id) {
        try {
            switch (action) {
                case CANCEL -> videoImportService.cancel(id);
                case RETRY -> videoImportService.retry(id);
            }
            return true;
        } catch (CustomException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Import job not found: " + id);
            }
            if (e.getMessageKey() == null && e.getMessage() != null) {
                throw CustomException.of(e.getStatus(), e.getSubCode(), e.getMessage());
            }
            throw e;
        }
    }
}
