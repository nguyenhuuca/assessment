package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportPreviewDto;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VideoImportService {

    /** Per-line results; only request-level problems (empty, too many, bad schedule) throw. */
    CreateVideoImportResultDto create(CreateVideoImportRequest request, Long adminId);

    /**
     * One job, scheduled now, for a URL that was already validated and resolved (e.g. an approved suggestion).
     * Makes no network call, so it is safe inside a transaction. Does not wake the worker.
     */
    CreateVideoImportResultDto.LineResult createResolved(ImportUrlValidator.ImportUrl url, String title, Long adminId);

    /** Newest first, optional status filter. */
    Page<VideoImportJobDto> list(ImportStatus status, Pageable pageable);

    ImportPreviewDto preview(String url);

    VideoImportJobDto runNow(Long id);

    VideoImportJobDto cancel(Long id);

    VideoImportJobDto retry(Long id);
}
