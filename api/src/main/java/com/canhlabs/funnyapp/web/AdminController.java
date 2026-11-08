package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.dto.admin.AdminHlsStatusDto;
import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.service.AdminVideoBulkService;
import jakarta.validation.Valid;
import java.util.Arrays;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
import com.canhlabs.funnyapp.utils.Contract;
import com.canhlabs.funnyapp.service.AdminVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminController {

    private AdminVideoService adminVideoService;
    private AdminVideoBulkService adminVideoBulkService;

    @Autowired
    public void injectAdminVideoBulkService(AdminVideoBulkService adminVideoBulkService) {
        this.adminVideoBulkService = adminVideoBulkService;
    }

    @Autowired
    public void injectAdminVideoService(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @GetMapping("/videos")
    public ResponseEntity<ResultObjectInfo<Page<AdminVideoDto>>> getVideos(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) VideoStatus status) {
        Page<AdminVideoDto> data = adminVideoService.getVideos(pageable, status);
        return ResponseEntity.ok(ResultObjectInfo.<Page<AdminVideoDto>>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @PatchMapping("/videos/{id}/status")
    public ResponseEntity<ResultObjectInfo<String>> updateVideoStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        VideoStatus status = VideoStatus.valueOf(body.get("status"));
        adminVideoService.updateStatus(id, status);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Status updated")
                .build());
    }

    @PatchMapping("/videos/{id}/priority")
    @AuditLog("updateVideoPriority")
    public ResponseEntity<ResultObjectInfo<String>> updateVideoPriority(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        adminVideoService.updatePriority(id, body.get("priority"));
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Priority updated")
                .build());
    }

    @PatchMapping("/videos/{id}/details")
    @AuditLog("updateVideoDetails")
    public ResponseEntity<ResultObjectInfo<String>> updateVideoDetails(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Object title = body == null ? null : body.get("title");
        Object description = body == null ? null : body.get("description");
        Contract.require(title instanceof String, "title is required");
        Contract.require(description == null || description instanceof String, "description must be a string");
        adminVideoService.updateDetails(id, (String) title, (String) description);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Details updated")
                .build());
    }

    @PatchMapping("/videos/{id}/visibility")
    @AuditLog("updateVideoVisibility")
    public ResponseEntity<ResultObjectInfo<String>> updateVideoVisibility(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        adminVideoService.updateVisibility(id, parseVisibility(body));
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Visibility updated")
                .build());
    }

    /** Body {visibility}; the legacy {hidden} body was removed in CO-F1 and is rejected. */
    private static VideoVisibility parseVisibility(Map<String, Object> body) {
        Object visibility = body == null ? null : body.get("visibility");
        Contract.require(visibility != null, "visibility is required");
        VideoVisibility parsed = Arrays.stream(VideoVisibility.values())
                .filter(v -> v.name().equals(visibility))
                .findFirst()
                .orElse(null);
        Contract.require(parsed != null, "invalid visibility");
        return parsed;
    }

    @DeleteMapping("/videos/{id}")
    @AuditLog("deleteVideo")
    public ResponseEntity<ResultObjectInfo<String>> deleteVideo(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean deleteLocalFile,
            @RequestParam(defaultValue = "false") boolean deleteDriveFile) {
        adminVideoService.deleteVideo(id, deleteLocalFile, deleteDriveFile);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Deleted")
                .build());
    }

    @PutMapping("/videos/{id}/hls")
    // one @PreAuthorize only: @HasPermission is itself a @PreAuthorize, so its check is inlined here
    @PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")
    @AuditLog("enableVideoHls")
    public ResponseEntity<ResultObjectInfo<AdminHlsStatusDto>> enableVideoHls(@PathVariable Long id) {
        return ResponseEntity.ok(ResultObjectInfo.<AdminHlsStatusDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(adminVideoService.enableHls(id))
                .build());
    }

    @DeleteMapping("/videos/{id}/hls")
    @PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")
    @AuditLog("disableVideoHls")
    public ResponseEntity<ResultObjectInfo<AdminHlsStatusDto>> disableVideoHls(@PathVariable Long id) {
        return ResponseEntity.ok(ResultObjectInfo.<AdminHlsStatusDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(adminVideoService.disableHls(id))
                .build());
    }

    @PostMapping("/videos/bulk")
    @AuditLog("bulkVideos")
    @HasPermission(perm = Permission.ADMIN)
    @RateLimited(permit = 10)
    public ResponseEntity<ResultObjectInfo<BulkResultDto>> bulkVideos(
            @Valid @RequestBody AdminVideoBulkRequest request) {
        BulkResultDto data = adminVideoBulkService.bulk(request);
        return ResponseEntity.ok(ResultObjectInfo.<BulkResultDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @GetMapping("/stats")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<AdminStatsDto>> getStats() {
        AdminStatsDto stats = adminVideoService.getStats();
        return ResponseEntity.ok(ResultObjectInfo.<AdminStatsDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(stats)
                .build());
    }
}
