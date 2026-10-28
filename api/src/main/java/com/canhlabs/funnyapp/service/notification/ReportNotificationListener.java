package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.ReportClosedEvent;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Tells the reporter that the GitHub issue of their report was closed. Runs on the async executor and never
 * lets an exception escape. fallbackExecution is on: the webhook stores the state without an outer transaction.
 */
@Slf4j
@Component
public class ReportNotificationListener {

    static final String GROUP_PREFIX = "report:";

    private final AppReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher publisher;
    private final JsonMapper objectMapper;

    public ReportNotificationListener(AppReportRepository reportRepository,
                                      NotificationRepository notificationRepository,
                                      NotificationPublisher publisher,
                                      JsonMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClosed(ReportClosedEvent event) {
        try {
            reportRepository.findFirstWithReporterByStatusAndIssueNumber(ReportStatus.CREATED, event.issueNumber())
                    .ifPresent(report -> notifyReporter(report, event.reason()));
        } catch (Exception e) {
            log.error("Failed to create closed notification for issue #{}", event.issueNumber(), e);
        }
    }

    private void notifyReporter(AppReport report, String reason) {
        User user = report.getReporter();
        if (user == null || user.getStatus() == UserStatus.DEACTIVATED) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("snippet", report.getTitle());
        if (reason != null) {
            payload.put("reason", reason);
        }
        String type = NotificationType.REPORT_CLOSED.name();
        try {
            notificationRepository.upsertGrouped(user.getId(), type, GROUP_PREFIX + report.getId() + ":" + type,
                    null, null, null, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to store closed notification for report {}", report.getId(), e);
            return;
        }
        publisher.unreadChanged(user.getId());
    }
}
