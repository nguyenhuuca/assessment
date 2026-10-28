package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.contract.JsonContractSupport;
import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.CommentModerationReason;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.CommentRemovedEvent;
import com.canhlabs.funnyapp.event.CommentRepliedEvent;
import com.canhlabs.funnyapp.event.ReportClosedEvent;
import com.canhlabs.funnyapp.event.SuggestionReviewedEvent;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSuggestionRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The notification {@code payload} string is written to the database by the listeners, so its JSON shape is a
 * persisted contract (spec spring-boot-4-upgrade Rule 2). Each listener gets the Boot auto-configured mapper.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPayloadContractTest {

    private static final JsonMapper MAPPER = JsonContractSupport.bootMapper();
    private static final UUID ROOT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID NEW_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID REPORT_ID = UUID.fromString("6f1c2b0e-1111-2222-3333-444455556666");

    @Mock NotificationRepository notificationRepository;
    @Mock VideoCommentRepository commentRepository;
    @Mock UserRepo userRepo;
    @Mock NotificationPublisher publisher;
    @Mock AppReportRepository reportRepository;
    @Mock VideoSuggestionRepository suggestionRepository;
    @Mock VideoImportJobRepository jobRepository;

    private static User user(long id, String email) {
        User u = new User();
        u.setId(id);
        u.setUserName(email);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private String capturedPayload() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(any(), anyString(), anyString(), any(), any(), any(),
                payload.capture());
        return payload.getValue();
    }

    @Test
    void commentReplied_payloadMatchesFixture() {
        NotificationEventListener listener = new NotificationEventListener(notificationRepository, commentRepository,
                userRepo, publisher, MAPPER);
        when(commentRepository.findById(ROOT_ID)).thenReturn(Optional.of(
                VideoComment.builder().id(ROOT_ID).videoId("22").userId("root@example.test").content("x").build()));
        when(commentRepository.findById(PARENT_ID)).thenReturn(Optional.empty());
        when(userRepo.findAllByUserName("root@example.test")).thenReturn(user(1L, "root@example.test"));

        listener.onCommentReplied(new CommentRepliedEvent("22", ROOT_ID.toString(), PARENT_ID.toString(), NEW_ID,
                "actor@example.test", "haha same \"quoted\""));

        JsonContractSupport.assertMatchesFixture("payload-comment-replied.json", capturedPayload());
    }

    @Test
    void commentRemoved_payloadMatchesFixture() {
        NotificationEventListener listener = new NotificationEventListener(notificationRepository, commentRepository,
                userRepo, publisher, MAPPER);
        when(userRepo.findAllByUserName("author@example.test")).thenReturn(user(2L, "author@example.test"));

        listener.onCommentRemoved(new CommentRemovedEvent(
                List.of(new CommentRemovedEvent.RemovedComment(NEW_ID, "22", "author@example.test")),
                CommentModerationReason.SPAM));

        JsonContractSupport.assertMatchesFixture("payload-comment-removed.json", capturedPayload());
    }

    @Test
    void reportClosed_payloadMatchesFixture() {
        ReportNotificationListener listener = new ReportNotificationListener(reportRepository, notificationRepository,
                publisher, MAPPER);
        AppReport row = AppReport.builder().id(REPORT_ID).reporter(user(7L, "reporter@example.test"))
                .title("Text is hard to read").status(ReportStatus.CREATED).issueNumber(40).build();
        when(reportRepository.findFirstWithReporterByStatusAndIssueNumber(ReportStatus.CREATED, 40))
                .thenReturn(Optional.of(row));

        listener.onClosed(new ReportClosedEvent(40, "completed"));

        JsonContractSupport.assertMatchesFixture("payload-report-closed.json", capturedPayload());
    }

    @Test
    void suggestionRejected_payloadMatchesFixture() {
        SuggestionNotificationListener listener = new SuggestionNotificationListener(notificationRepository,
                suggestionRepository, jobRepository, userRepo, publisher, MAPPER);
        when(userRepo.findById(7L)).thenReturn(Optional.of(user(7L, "suggester@example.test")));

        listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, false, "Funny cat", "COPYRIGHT"));

        JsonContractSupport.assertMatchesFixture("payload-suggestion-rejected.json", capturedPayload());
    }

    @Test
    void suggestionApproved_payloadMatchesFixture() {
        SuggestionNotificationListener listener = new SuggestionNotificationListener(notificationRepository,
                suggestionRepository, jobRepository, userRepo, publisher, MAPPER);
        when(userRepo.findById(7L)).thenReturn(Optional.of(user(7L, "suggester@example.test")));

        listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, true, "Funny cat", null));

        JsonContractSupport.assertMatchesFixture("payload-suggestion-approved.json", capturedPayload());
    }
}
