package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.CommentModerationReason;
import com.canhlabs.funnyapp.enums.CommentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommentDto {
    private UUID id;
    private String videoId;
    private String videoTitle;
    private String authorEmail;
    private String guestName;
    private Boolean isGuest;
    private String content;
    private String parentId;
    private Instant createdAt;
    private CommentStatus status;
    private CommentModerationReason moderationReason;
    private String moderationNote;
    private String moderatedBy;
    private Instant moderatedAt;
}
