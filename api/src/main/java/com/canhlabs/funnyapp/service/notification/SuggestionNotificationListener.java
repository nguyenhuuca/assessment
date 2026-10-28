package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoSuggestion;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.SuggestionReviewedEvent;
import com.canhlabs.funnyapp.event.VideoIngestedEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSuggestionRepository;
import com.canhlabs.funnyapp.service.impl.VideoSuggestionServiceImpl;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns suggestion events into notifications for the suggester. Runs after the producing transaction
 * committed, on the async executor, and never lets an exception escape.
 * <p>
 * fallbackExecution is on for the ingest event: the cron saves the video without an outer transaction,
 * and such events would otherwise be dropped.
 */
@Slf4j
@Component
public class SuggestionNotificationListener {

    static final String GROUP_PREFIX = "suggestion:";

    private final NotificationRepository notificationRepository;
    private final VideoSuggestionRepository suggestionRepository;
    private final VideoImportJobRepository jobRepository;
    private final UserRepo userRepo;
    private final NotificationPublisher publisher;
    private final JsonMapper objectMapper;

    public SuggestionNotificationListener(NotificationRepository notificationRepository,
                                          VideoSuggestionRepository suggestionRepository,
                                          VideoImportJobRepository jobRepository,
                                          UserRepo userRepo,
                                          NotificationPublisher publisher,
                                          JsonMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.suggestionRepository = suggestionRepository;
        this.jobRepository = jobRepository;
        this.userRepo = userRepo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewed(SuggestionReviewedEvent event) {
        try {
            NotificationType type = event.approved()
                    ? NotificationType.SUGGESTION_APPROVED : NotificationType.SUGGESTION_REJECTED;
            deliver(event.userId(), type, event.suggestionId(), null, event.snippet(), event.reason());
        } catch (Exception e) {
            log.error("Failed to create review notification for suggestion {}", event.suggestionId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVideoIngested(VideoIngestedEvent event) {
        try {
            jobRepository.findFirstByDriveFileId(event.driveFileId())
                    .flatMap(job -> suggestionRepository.findFirstByImportJobId(job.getId()))
                    .filter(s -> s.getStatus() == SuggestionStatus.APPROVED)
                    .ifPresent(s -> notifyLive(s, event.videoSourceId()));
        } catch (Exception e) {
            log.error("Failed to create live notification for Drive file {}", event.driveFileId(), e);
        }
    }

    private void notifyLive(VideoSuggestion s, Long videoSourceId) {
        try {
            deliver(s.getUserId(), NotificationType.SUGGESTION_LIVE, s.getId(),
                    String.valueOf(videoSourceId), VideoSuggestionServiceImpl.snippetOf(s), null);
        } catch (Exception e) {
            log.error("Failed to deliver live notification for suggestion {}", s.getId(), e);
        }
    }

    private void deliver(Long userId, NotificationType type, Long suggestionId, String videoId,
                         String snippet, String reason) {
        User user = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (user == null || user.getStatus() == UserStatus.DEACTIVATED) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("snippet", snippet == null ? "" : snippet);
        if (reason != null) {
            payload.put("reason", reason);
        }
        notificationRepository.upsertGrouped(userId, type.name(), GROUP_PREFIX + suggestionId + ":" + type.name(),
                videoId, null, null, objectMapper.writeValueAsString(payload));
        publisher.unreadChanged(userId);
    }
}
