package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.service.AdminVideoBulkService;
import com.canhlabs.funnyapp.service.AdminVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bulk orchestration only (ADR-0036): not transactional, each row calls the single-row
 * {@link AdminVideoService} method, so all its guards and side effects apply and a failing row
 * never affects the others. A row is as atomic as that single-row method.
 */
@Service
public class AdminVideoBulkServiceImpl implements AdminVideoBulkService {
    private AdminVideoService adminVideoService;

    @Autowired
    public void injectAdminVideoService(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @Override
    public BulkResultDto bulk(AdminVideoBulkRequest request) {
        List<Long> ids = request.distinctIds();
        BulkResultCollector collector = new BulkResultCollector("videos:" + request.getAction());
        for (Long id : ids) {
            collector.run(id, () -> applyRow(request, id));
        }
        return collector.build(ids.size());
    }

    private boolean applyRow(AdminVideoBulkRequest request, Long id) {
        return switch (request.getAction()) {
            case STATUS -> adminVideoService.updateStatus(id, request.getStatus());
            case VISIBILITY -> adminVideoService.updateVisibility(id, request.getVisibility());
            case DELETE -> {
                // Drive files are never deleted in bulk (ADR-0036).
                adminVideoService.deleteVideo(id, request.shouldDeleteLocalFile(), false);
                yield true;
            }
        };
    }
}
