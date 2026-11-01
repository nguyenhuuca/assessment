package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.AdminVideoBulkService;
import com.canhlabs.funnyapp.service.AdminVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Not transactional on purpose: each row goes through {@link AdminVideoService}, which keeps its own
 * transaction, so a failing row rolls back only itself (ADR-0036).
 */
@Service
@Slf4j
public class AdminVideoBulkServiceImpl implements AdminVideoBulkService {
    private static final String NOT_FOUND_PREFIX = "Video not found";
    private static final String CONFLICT_CODE = "error.conflict";

    private AdminVideoService adminVideoService;

    @Autowired
    public void injectAdminVideoService(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @Override
    public BulkResultDto bulk(AdminVideoBulkRequest request) {
        List<Long> ids = request.distinctIds();
        BulkResultCollector collector = new BulkResultCollector();
        for (Long id : ids) {
            applyRow(request, id, collector);
        }
        return collector.build(ids.size());
    }

    private void applyRow(AdminVideoBulkRequest request, Long id, BulkResultCollector collector) {
        try {
            boolean changed = switch (request.getAction()) {
                case STATUS -> adminVideoService.updateStatus(id, request.getStatus());
                case VISIBILITY -> adminVideoService.updateVisibility(id, request.getVisibility());
                case DELETE -> {
                    // Drive files are never deleted in bulk (ADR-0036).
                    adminVideoService.deleteVideo(id, request.shouldDeleteLocalFile(), false);
                    yield true;
                }
            };
            if (changed) {
                collector.updated();
            } else {
                collector.unchanged();
            }
        } catch (CustomException e) {
            collector.skipped(id, e);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(NOT_FOUND_PREFIX)) {
                collector.notFound(id);
            } else {
                collector.skipped(id, BulkResultCollector.UNEXPECTED_CODE, e.getMessage());
            }
        } catch (IllegalStateException e) {
            // e.g. the local file is being synced or could not be removed; the row is left intact.
            collector.skipped(id, CONFLICT_CODE, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Bulk video {} failed for id {}: {}", request.getAction(), id, e.getClass().getSimpleName());
            collector.skipped(id, BulkResultCollector.UNEXPECTED_CODE, "Unexpected error");
        }
    }
}
