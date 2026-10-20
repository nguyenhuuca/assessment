package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.CreateReportRequest;
import com.canhlabs.funnyapp.dto.admin.ReportDto;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** In-app reports turned into GitHub issues (ADR-0026). */
public interface ReportService {

    /** Stores the report, then creates its GitHub issue; a GitHub failure leaves a FAILED row, not an error. */
    ReportDto create(CreateReportRequest request);

    /** Newest first, with the GitHub state of CREATED rows. */
    Page<ReportDto> list(int page, int size);

    /** Repeats the GitHub call for a FAILED report. */
    ReportDto retry(UUID id);
}
