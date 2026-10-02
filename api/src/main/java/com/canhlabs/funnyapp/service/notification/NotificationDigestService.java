package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.Notification;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Email digest (one plain-text mail per opted-in user per run) and read-notification retention.
 * Shares the daily email budget with magic links, so it stops at {@value #BUDGET_PERCENT}% of the daily maximum.
 */
@Slf4j
@Service
public class NotificationDigestService {

    static final int BUDGET_PERCENT = 50;
    static final int MAX_LISTED = 5;
    static final Duration RETENTION = Duration.ofDays(90);

    private final NotificationRepository notificationRepository;
    private final UserRepo userRepo;
    private final MailService mailService;
    private final EmailCacheLimiter emailLimiter;
    private final AppProperties appProperties;

    public NotificationDigestService(NotificationRepository notificationRepository, UserRepo userRepo,
                                     MailService mailService, EmailCacheLimiter emailLimiter,
                                     AppProperties appProperties) {
        this.notificationRepository = notificationRepository;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.emailLimiter = emailLimiter;
        this.appProperties = appProperties;
    }

    /**
     * Sends the digests; returns the number of emails sent.
     */
    public int sendDigests() {
        int sent = 0;
        for (Long userId : notificationRepository.findUserIdsForDigest()) {
            if (budgetExhausted()) {
                log.warn("Notification digest stopped: {}% of the daily email budget is used", BUDGET_PERCENT);
                break;
            }
            try {
                if (sendDigest(userId)) {
                    sent++;
                }
            } catch (Exception e) {
                // Leave emailed_at unset so the next run retries this user
                log.error("Notification digest failed for user id {}", userId, e);
            }
        }
        return sent;
    }

    /**
     * Deletes read notifications older than the retention window; returns the number of deleted rows.
     */
    public int purgeOldRead(Instant now) {
        return notificationRepository.deleteReadOlderThan(now.minus(RETENTION));
    }

    private boolean budgetExhausted() {
        int max = appProperties.getEmailSetting().getMaxDailyEmails();
        return emailLimiter.getDailyCount(LocalDate.now()) * 100L >= (long) max * BUDGET_PERCENT;
    }

    private boolean sendDigest(Long userId) {
        User user = userRepo.findAllById(userId);
        if (user == null || user.getStatus() == UserStatus.DEACTIVATED
                || user.getUserName() == null || user.getUserName().isBlank()) {
            return false;
        }
        List<Notification> pending = notificationRepository
                .findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(userId);
        if (pending.isEmpty()) {
            return false;
        }
        mailService.sendSimpleMail(user.getUserName(), subject(pending.size()), body(pending));
        emailLimiter.incrementDailyCount(LocalDate.now());
        List<UUID> ids = pending.stream().map(Notification::getId).toList();
        notificationRepository.markEmailed(ids, Instant.now());
        return true;
    }

    static String subject(int count) {
        return "Bạn có " + count + " thông báo mới trên Funny Movies";
    }

    String body(List<Notification> pending) {
        StringBuilder sb = new StringBuilder("Xin chào,\n\nBạn có thông báo mới:\n\n");
        pending.stream().limit(MAX_LISTED).forEach(n -> sb.append("- ").append(describe(n)).append('\n'));
        if (pending.size() > MAX_LISTED) {
            sb.append("\nvà ").append(pending.size() - MAX_LISTED).append(" thông báo khác.\n");
        }
        sb.append("\nXem tại: ").append(appProperties.getDomain()).append('\n');
        sb.append("\n--\nTắt email thông báo trong Cài đặt.\n");
        return sb.toString();
    }

    private static String describe(Notification n) {
        if (n.getType() == NotificationType.COMMENT_REMOVED) {
            Object reason = n.getPayload() == null ? null : n.getPayload().get("reason");
            return "Bình luận của bạn đã bị gỡ" + (reason == null ? "" : " (" + reason + ")");
        }
        String actor = n.getActorDisplay() == null ? "Ai đó" : n.getActorDisplay();
        String who = n.getActorCount() > 1 ? actor + " và " + (n.getActorCount() - 1) + " người khác" : actor;
        Object snippet = n.getPayload() == null ? null : n.getPayload().get("snippet");
        String text = who + " đã trả lời bình luận của bạn";
        return snippet == null || snippet.toString().isBlank() ? text : text + ": \"" + snippet + "\"";
    }
}
