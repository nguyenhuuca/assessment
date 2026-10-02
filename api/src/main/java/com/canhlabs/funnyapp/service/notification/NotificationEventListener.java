package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.CommentRemovedEvent;
import com.canhlabs.funnyapp.event.CommentRepliedEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.utils.NotificationText;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns comment events into notification rows. Runs after the producing transaction committed, on the
 * async (virtual-thread) executor, and never lets an exception escape: notifications must not affect
 * the user action that triggered them.
 */
@Slf4j
@Component
public class NotificationEventListener {

    static final String REPLY_GROUP_PREFIX = "REPLY:";
    static final String REMOVED_GROUP_PREFIX = "REMOVED:";
    static final String GUEST_DISPLAY = "guest";

    private final NotificationRepository notificationRepository;
    private final VideoCommentRepository commentRepository;
    private final UserRepo userRepo;
    private final NotificationPublisher publisher;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     VideoCommentRepository commentRepository,
                                     UserRepo userRepo,
                                     NotificationPublisher publisher,
                                     ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.userRepo = userRepo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentReplied(CommentRepliedEvent event) {
        try {
            String groupKey = REPLY_GROUP_PREFIX + event.rootId();
            String payload = toJson(Map.of("snippet", event.snippet() == null ? "" : event.snippet()));
            String display = NotificationText.actorDisplay(event.actorEmail());
            String actorDisplay = display == null ? GUEST_DISPLAY : display;
            for (String email : replyRecipients(event)) {
                try {
                    deliver(email, NotificationType.COMMENT_REPLY, groupKey, event.videoId(),
                            event.commentId(), actorDisplay, payload);
                } catch (Exception e) {
                    log.error("Failed to deliver reply notification for comment {}", event.commentId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create reply notifications for comment {}", event.commentId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentRemoved(CommentRemovedEvent event) {
        try {
            // Reason code only: removed content is never copied into a notification
            String payload = toJson(Map.of("reason", event.reason() == null ? "OTHER" : event.reason().name()));
            for (CommentRemovedEvent.RemovedComment removed : event.comments()) {
                try {
                    deliver(removed.authorEmail(), NotificationType.COMMENT_REMOVED,
                            REMOVED_GROUP_PREFIX + removed.commentId(), removed.videoId(),
                            removed.commentId(), null, payload);
                } catch (Exception e) {
                    log.error("Failed to create removal notification for comment {}", removed.commentId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create removal notifications", e);
        }
    }

    /**
     * {thread root author, replied-to author} minus the actor, guests and duplicates (case-insensitive).
     */
    private Iterable<String> replyRecipients(CommentRepliedEvent event) {
        Map<String, String> recipients = new LinkedHashMap<>();
        for (String commentId : new String[]{event.rootId(), event.parentId()}) {
            String author = authorOf(commentId);
            if (author == null || author.isBlank()) {
                continue; // guest or unknown comment
            }
            if (event.actorEmail() != null && author.equalsIgnoreCase(event.actorEmail())) {
                continue;
            }
            recipients.putIfAbsent(author.toLowerCase(), author);
        }
        return recipients.values();
    }

    private String authorOf(String commentId) {
        if (commentId == null) {
            return null;
        }
        try {
            return commentRepository.findById(UUID.fromString(commentId))
                    .map(VideoComment::getUserId)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void deliver(String email, NotificationType type, String groupKey, String videoId,
                         UUID commentId, String actorDisplay, String payload) {
        if (email == null || email.isBlank()) {
            return;
        }
        User user = userRepo.findAllByUserName(email);
        if (user == null || user.getStatus() == UserStatus.DEACTIVATED) {
            return;
        }
        notificationRepository.upsertGrouped(user.getId(), type.name(), groupKey, videoId,
                commentId == null ? null : commentId.toString(), actorDisplay, payload);
        publisher.unreadChanged(user.getId());
    }

    private String toJson(Map<String, String> payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
