package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.BulkModerateCommentRequest;
import com.canhlabs.funnyapp.dto.admin.BulkModerationResultDto;
import com.canhlabs.funnyapp.dto.admin.HardDeleteCommentsResultDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminCommentService {
    Page<AdminCommentDto> getComments(Pageable pageable, CommentStatus status, String q, String videoId);

    AdminCommentDto moderate(UUID id, ModerateCommentRequest request);

    BulkModerationResultDto bulkModerate(BulkModerateCommentRequest request);

    /** Permanently deletes the comments and all their replies. */
    HardDeleteCommentsResultDto hardDelete(List<UUID> ids);
}
