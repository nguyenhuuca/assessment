package com.canhlabs.funnyapp.event;

import java.util.UUID;

/**
 * Published after a reply is saved. parentId is the comment the user actually replied to
 * (before normalization to the thread root); rootId is the thread root.
 */
public record CommentRepliedEvent(String videoId, String rootId, String parentId, UUID commentId,
                                  String actorEmail, String snippet) {
}
