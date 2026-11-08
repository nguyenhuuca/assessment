package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.BulkModerateCommentRequest;
import com.canhlabs.funnyapp.dto.admin.BulkModerationResultDto;
import com.canhlabs.funnyapp.dto.admin.HardDeleteCommentsRequest;
import com.canhlabs.funnyapp.dto.admin.HardDeleteCommentsResultDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.AdminCommentService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private AdminCommentService adminCommentService;

    @Autowired
    public void injectAdminCommentService(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
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

    /** Permanent delete (with replies); REMOVE moderation is the reversible alternative. */
    @PostMapping("/comments/hard-delete")
    @AuditLog("hardDeleteComments")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<HardDeleteCommentsResultDto>> hardDeleteComments(
            @Valid @RequestBody HardDeleteCommentsRequest request) {
        HardDeleteCommentsResultDto data = adminCommentService.hardDelete(request.getIds());
        return ResponseEntity.ok(ResultObjectInfo.<HardDeleteCommentsResultDto>builder()
                .status(ResultStatus.SUCCESS)
                .message("Comments deleted")
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
}
