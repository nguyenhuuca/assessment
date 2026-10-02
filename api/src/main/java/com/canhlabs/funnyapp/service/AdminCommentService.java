package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminCommentService {
    Page<AdminCommentDto> getComments(Pageable pageable, CommentStatus status, String q, String videoId);

    AdminCommentDto moderate(UUID id, ModerateCommentRequest request);
}
