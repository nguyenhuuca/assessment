package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportBulkRequest;

public interface ImportBulkService {
    /** Cancels or retries each distinct import job on its own; one failing row never stops the rest. */
    BulkResultDto bulk(ImportBulkRequest request);
}
