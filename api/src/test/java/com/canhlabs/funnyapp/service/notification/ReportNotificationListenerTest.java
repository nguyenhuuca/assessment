package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.ReportClosedEvent;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportNotificationListenerTest {

    private static final UUID REPORT_ID = UUID.fromString("6f1c2b0e-1111-2222-3333-444455556666");

    @Mock AppReportRepository reportRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPublisher publisher;

    ReportNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReportNotificationListener(reportRepository, notificationRepository, publisher, new ObjectMapper());
    }

    private static User user(UserStatus status) {
        User u = new User();
        u.setId(7L);
        u.setUserName("admin@x.com");
        u.setStatus(status);
        return u;
    }

    private void report(User reporter) {
        AppReport row = AppReport.builder().id(REPORT_ID).reporter(reporter).title("Text is hard to read")
                .status(ReportStatus.CREATED).issueNumber(40).build();
        when(reportRepository.findFirstWithReporterByStatusAndIssueNumber(ReportStatus.CREATED, 40))
                .thenReturn(Optional.of(row));
    }

    @Test
    void closed_notifiesTheReporterWithTitleAndReason_andPingsSse() {
        report(user(UserStatus.ACTIVE));

        listener.onClosed(new ReportClosedEvent(40, "completed"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(7L), eq("REPORT_CLOSED"),
                eq("report:" + REPORT_ID + ":REPORT_CLOSED"), isNull(), isNull(), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("\"snippet\":\"Text is hard to read\"")
                .contains("\"reason\":\"completed\"").doesNotContain("admin@x.com");
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void closedWithoutReason_payloadHasNoReason() {
        report(user(UserStatus.ACTIVE));

        listener.onClosed(new ReportClosedEvent(40, null));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(7L), anyString(), anyString(), isNull(), isNull(), isNull(),
                payload.capture());
        assertThat(payload.getValue()).doesNotContain("reason");
    }

    @Test
    void deletedReporter_noNotification() {
        report(null);

        listener.onClosed(new ReportClosedEvent(40, "completed"));

        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void deactivatedReporter_noNotification() {
        report(user(UserStatus.DEACTIVATED));

        listener.onClosed(new ReportClosedEvent(40, "completed"));

        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void noReportForIssue_noNotification() {
        when(reportRepository.findFirstWithReporterByStatusAndIssueNumber(any(), any())).thenReturn(Optional.empty());

        listener.onClosed(new ReportClosedEvent(99, "completed"));

        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void lookupFailure_neverEscapes() {
        when(reportRepository.findFirstWithReporterByStatusAndIssueNumber(any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onClosed(new ReportClosedEvent(40, null))).doesNotThrowAnyException();
        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void insertFailure_neverEscapes_andSkipsSse() {
        report(user(UserStatus.ACTIVE));
        when(notificationRepository.upsertGrouped(anyLong(), anyString(), anyString(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("insert failed"));
        assertThatCode(() -> listener.onClosed(new ReportClosedEvent(40, null))).doesNotThrowAnyException();
        verify(publisher, never()).unreadChanged(anyLong());
    }
}
