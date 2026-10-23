package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.CreateReportRequest;
import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.ReportService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** In-app reports that become GitHub issues (ADR-0026); open to CONTRIBUTOR, ADMIN and SUPER_ADMIN (no permission bit, ADR-0030). */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/reports")
@PreAuthorize("hasRole('CONTRIBUTOR')")
public class AdminReportController {

    private ReportService reportService;

    @Autowired
    public void injectReportService(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @AuditLog("createReport")
    @RateLimited(permit = 5, windowInSeconds = 3600)
    public ResponseEntity<ResultObjectInfo<ReportDto>> create(@RequestBody CreateReportRequest request) {
        return ResponseEntity.ok(wrap(reportService.create(request)));
    }

    @GetMapping
    @RateLimited(permit = 60)
    public ResponseEntity<ResultObjectInfo<Page<ReportDto>>> list(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(wrap(reportService.list(page, size)));
    }

    @PostMapping("/{id}/retry")
    @AuditLog("retryReport")
    @RateLimited(permit = 5, windowInSeconds = 3600)
    public ResponseEntity<ResultObjectInfo<ReportDto>> retry(@PathVariable String id) {
        return ResponseEntity.ok(wrap(reportService.retry(parseId(id))));
    }

    /** A malformed id cannot name a report, so it is a 404 rather than a conversion error. */
    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw CustomException.of(HttpStatus.NOT_FOUND, 404, "error.report.notFound");
        }
    }

    private static <T> ResultObjectInfo<T> wrap(T data) {
        return ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build();
    }
}
