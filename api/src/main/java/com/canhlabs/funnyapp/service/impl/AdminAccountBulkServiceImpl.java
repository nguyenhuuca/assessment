package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminAccountBulkRequest;
import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.service.AdminAccountBulkService;
import com.canhlabs.funnyapp.service.AdminAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bulk orchestration only (ADR-0036): not transactional, each row calls the single-row
 * {@link AdminAccountService} method, so self / rank / autopilot guards apply per row. The permission
 * bit is applied to the target's current mask, read fresh per row, never to a client-supplied mask.
 */
@Service
public class AdminAccountBulkServiceImpl implements AdminAccountBulkService {
    private AdminAccountService adminAccountService;

    @Autowired
    public void injectAdminAccountService(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Override
    public BulkResultDto bulk(AdminAccountBulkRequest request, Long actorId) {
        List<Long> ids = request.distinctIds();
        BulkResultCollector collector = new BulkResultCollector("accounts:" + request.getAction());
        for (Long id : ids) {
            collector.run(id, () -> applyRow(request, id, actorId));
        }
        return collector.build(ids.size());
    }

    private boolean applyRow(AdminAccountBulkRequest request, Long id, Long actorId) {
        return switch (request.getAction()) {
            case ROLE -> {
                AdminAccountDto current = adminAccountService.getAccount(id);
                // Self rows always go to the service so the self rule decides, never "unchanged".
                if (!id.equals(actorId) && current.getRole() == request.getRole()) {
                    yield false;
                }
                adminAccountService.updateRole(id, actorId, request.getRole());
                yield true;
            }
            case GRANT_PERMISSION -> changeBit(id, actorId, request.getPermission().getBit(), true);
            case REVOKE_PERMISSION -> changeBit(id, actorId, request.getPermission().getBit(), false);
            case DELETE -> {
                adminAccountService.deleteAccount(id, actorId);
                yield true;
            }
        };
    }

    private boolean changeBit(Long id, Long actorId, int bit, boolean grant) {
        int current = adminAccountService.getAccount(id).getPermissions();
        int mask = grant ? current | bit : current & ~bit;
        if (!id.equals(actorId) && mask == current) {
            return false;
        }
        adminAccountService.updatePermissions(id, actorId, mask);
        return true;
    }
}
