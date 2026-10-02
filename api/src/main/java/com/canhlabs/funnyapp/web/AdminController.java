package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.BulkModerateCommentRequest;
import com.canhlabs.funnyapp.dto.admin.BulkModerationResultDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.service.AdminCommentService;
import jakarta.validation.Valid;
import java.util.UUID;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.service.AdminAccountService;
import com.canhlabs.funnyapp.service.AdminVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
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
    private AdminAccountService adminAccountService;
    private AdminCommentService adminCommentService;

    @Autowired
    public void injectAdminVideoService(AdminVideoService adminVideoService) {
        this.adminVideoService = adminVideoService;
    }

    @Autowired
    public void injectAdminAccountService(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Autowired
    public void injectAdminCommentService(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
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

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<ResultObjectInfo<String>> deleteVideo(@PathVariable Long id) {
        adminVideoService.deleteVideo(id);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Deleted")
                .build());
    }

    @GetMapping("/accounts")
    public ResponseEntity<ResultObjectInfo<Page<AdminAccountDto>>> getAccounts(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdminAccountDto> data = adminAccountService.getAccounts(pageable);
        return ResponseEntity.ok(ResultObjectInfo.<Page<AdminAccountDto>>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @PatchMapping("/accounts/{id}/role")
    public ResponseEntity<ResultObjectInfo<String>> updateAccountRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        UserDetailDto current = AppUtils.getCurrentUser();
        UserRole role = UserRole.valueOf(body.get("role"));
        adminAccountService.updateRole(id, current.getId(), role);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Role updated")
                .build());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<ResultObjectInfo<String>> deleteAccount(@PathVariable Long id) {
        UserDetailDto current = AppUtils.getCurrentUser();
        adminAccountService.deleteAccount(id, current.getId());
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Account deleted")
                .build());
    }

    @GetMapping("/comments")
    public ResponseEntity<ResultObjectInfo<Page<AdminCommentDto>>> getComments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) CommentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String videoId) {
        Page<AdminCommentDto> data = adminCommentService.getComments(pageable, status, q, videoId);
        return ResponseEntity.ok(ResultObjectInfo.<Page<AdminCommentDto>>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @PatchMapping("/comments/moderation")
    @AuditLog("bulkModerateComments")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<BulkModerationResultDto>> bulkModerateComments(
            @Valid @RequestBody BulkModerateCommentRequest request) {
        BulkModerationResultDto data = adminCommentService.bulkModerate(request);
        return ResponseEntity.ok(ResultObjectInfo.<BulkModerationResultDto>builder()
                .status(ResultStatus.SUCCESS)
                .message("Comments moderated")
                .data(data)
                .build());
    }

    @PatchMapping("/comments/{id}/moderation")
    @AuditLog("moderateComment")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<AdminCommentDto>> moderateComment(
            @PathVariable UUID id,
            @Valid @RequestBody ModerateCommentRequest request) {
        AdminCommentDto data = adminCommentService.moderate(id, request);
        return ResponseEntity.ok(ResultObjectInfo.<AdminCommentDto>builder()
                .status(ResultStatus.SUCCESS)
                .message("Comment moderated")
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
