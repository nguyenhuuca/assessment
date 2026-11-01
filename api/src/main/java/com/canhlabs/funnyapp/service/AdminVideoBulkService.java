package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;

public interface AdminVideoBulkService {
    /** Applies the action to each distinct id on its own; one failing row never stops the rest. */
    BulkResultDto bulk(AdminVideoBulkRequest request);
}
