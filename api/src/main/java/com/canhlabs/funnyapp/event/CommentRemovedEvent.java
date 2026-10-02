package com.canhlabs.funnyapp.event;

import com.canhlabs.funnyapp.enums.CommentModerationReason;

import java.util.List;
import java.util.UUID;

/**
 * Published after moderation changed one or more comments to REMOVED. Carries no comment content.
 */
public record CommentRemovedEvent(List<RemovedComment> comments, CommentModerationReason reason) {

    public record RemovedComment(UUID commentId, String videoId, String authorEmail) {
    }
}
