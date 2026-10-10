package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportPreviewDto;
import com.canhlabs.funnyapp.dto.admin.PreviewImportRequest;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.VideoImportService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/video-imports")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminVideoImportController {

    private VideoImportService videoImportService;

    @Autowired
    public void injectVideoImportService(VideoImportService videoImportService) {
        this.videoImportService = videoImportService;
    }

    @PostMapping
    @AuditLog("createVideoImports")
    @HasPermission(perm = Permission.ADMIN)
    @RateLimited(permit = 10)
    public ResponseEntity<ResultObjectInfo<CreateVideoImportResultDto>> create(
            @RequestBody CreateVideoImportRequest request) {
        UserDetailDto current = AppUtils.getCurrentUser();
        CreateVideoImportResultDto data = videoImportService.create(request, current == null ? null : current.getId());
        return ok(data);
    }

    @GetMapping
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<Page<VideoImportJobDto>>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) ImportStatus status) {
        return ok(videoImportService.list(status, pageable));
    }

    @PostMapping("/preview")
    @RateLimited(permit = 20)
    @AuditLog("previewVideoImport")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<ImportPreviewDto>> preview(@RequestBody PreviewImportRequest request) {
        return ok(videoImportService.preview(request == null ? null : request.getUrl()));
    }

    @PostMapping("/{id}/run-now")
    @AuditLog("runVideoImportNow")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<VideoImportJobDto>> runNow(@PathVariable Long id) {
        return ok(videoImportService.runNow(id));
    }

    @PostMapping("/{id}/cancel")
    @AuditLog("cancelVideoImport")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<VideoImportJobDto>> cancel(@PathVariable Long id) {
        return ok(videoImportService.cancel(id));
    }

    @PostMapping("/{id}/retry")
    @AuditLog("retryVideoImport")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<VideoImportJobDto>> retry(@PathVariable Long id) {
        return ok(videoImportService.retry(id));
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> ok(T data) {
        return ResponseEntity.ok(ResultObjectInfo.<T>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }
}
