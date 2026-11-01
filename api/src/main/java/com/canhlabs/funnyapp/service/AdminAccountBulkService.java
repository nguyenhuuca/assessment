package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminAccountBulkRequest;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;

public interface AdminAccountBulkService {
    /** Applies the action to each distinct id on its own as {@code actorId}; one failing row never stops the rest. */
    BulkResultDto bulk(AdminAccountBulkRequest request, Long actorId);
}
