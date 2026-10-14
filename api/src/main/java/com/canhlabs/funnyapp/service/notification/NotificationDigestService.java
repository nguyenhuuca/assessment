package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.Notification;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.EmailLocaleResolver;
import com.canhlabs.funnyapp.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final EmailLocaleResolver localeResolver;
    private final MessageSource messageSource;

    public NotificationDigestService(NotificationRepository notificationRepository, UserRepo userRepo,
                                     MailService mailService, EmailCacheLimiter emailLimiter,
                                     AppProperties appProperties, EmailLocaleResolver localeResolver,
                                     MessageSource messageSource) {
        this.notificationRepository = notificationRepository;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.emailLimiter = emailLimiter;
        this.appProperties = appProperties;
        this.localeResolver = localeResolver;
        this.messageSource = messageSource;
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
        Locale locale = localeResolver.forUser(userId);
        mailService.sendSimpleMail(user.getUserName(), subject(pending.size(), locale), body(pending, locale));
        emailLimiter.incrementDailyCount(LocalDate.now());
        List<UUID> ids = pending.stream().map(Notification::getId).toList();
        notificationRepository.markEmailed(ids, Instant.now());
        return true;
    }

    String subject(int count, Locale locale) {
        return msg("mail.digest.subject", locale, String.valueOf(count));
    }

    String body(List<Notification> pending, Locale locale) {
        StringBuilder sb = new StringBuilder(msg("mail.digest.header", locale));
        pending.stream().limit(MAX_LISTED).forEach(n -> sb.append("- ").append(describe(n, locale)).append('\n'));
        if (pending.size() > MAX_LISTED) {
            sb.append(msg("mail.digest.more", locale, String.valueOf(pending.size() - MAX_LISTED)));
        }
        sb.append(msg("mail.digest.view", locale, appProperties.getDomain()));
        sb.append(msg("mail.digest.footer", locale));
        return sb.toString();
    }

    private String describe(Notification n, Locale locale) {
        if (n.getType() == NotificationType.SUGGESTION_APPROVED
                || n.getType() == NotificationType.SUGGESTION_REJECTED
                || n.getType() == NotificationType.SUGGESTION_LIVE) {
            return describeSuggestion(n, locale);
        }
        if (n.getType() == NotificationType.COMMENT_REMOVED) {
            Object reason = n.getPayload() == null ? null : n.getPayload().get("reason");
            return reason == null ? msg("mail.digest.item.removed", locale)
                    : msg("mail.digest.item.removedReason", locale, reason.toString());
        }
        String actor = n.getActorDisplay() == null ? msg("mail.digest.item.someone", locale) : n.getActorDisplay();
        String who = n.getActorCount() > 1
                ? msg("mail.digest.item.actors", locale, actor, String.valueOf(n.getActorCount() - 1)) : actor;
        Object snippet = n.getPayload() == null ? null : n.getPayload().get("snippet");
        return snippet == null || snippet.toString().isBlank()
                ? msg("mail.digest.item.reply", locale, who)
                : msg("mail.digest.item.replySnippet", locale, who, snippet.toString());
    }

    private String describeSuggestion(Notification n, Locale locale) {
        Map<String, Object> payload = n.getPayload() == null ? Map.of() : n.getPayload();
        String title = payload.get("snippet") == null ? "" : payload.get("snippet").toString();
        Object reason = payload.get("reason");
        return switch (n.getType()) {
            case SUGGESTION_APPROVED -> msg("mail.digest.item.suggestionApproved", locale, title);
            case SUGGESTION_LIVE -> msg("mail.digest.item.suggestionLive", locale, title);
            default -> reason == null ? msg("mail.digest.item.suggestionRejected", locale, title)
                    : msg("mail.digest.item.suggestionRejectedReason", locale, title, reason.toString());
        };
    }

    private String msg(String key, Locale locale, String... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, locale);
    }
}
