package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.entity.VideoSuggestion;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.SuggestionReviewedEvent;
import com.canhlabs.funnyapp.event.VideoIngestedEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSuggestionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuggestionNotificationListenerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock VideoSuggestionRepository suggestionRepository;
    @Mock VideoImportJobRepository jobRepository;
    @Mock UserRepo userRepo;
    @Mock NotificationPublisher publisher;

    SuggestionNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SuggestionNotificationListener(notificationRepository, suggestionRepository, jobRepository,
                userRepo, publisher, new ObjectMapper());
        when(userRepo.findById(7L)).thenReturn(Optional.of(user(7L, UserStatus.ACTIVE)));
    }

    private static User user(long id, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setUserName("u" + id + "@x.com");
        u.setStatus(status);
        return u;
    }

    private static VideoSuggestion suggestion(SuggestionStatus status) {
        return VideoSuggestion.builder().id(5L).userId(7L).sourceUrl("https://youtu.be/dQw4w9WgXcQ")
                .normalizedUrl("n").platform(ImportPlatform.YOUTUBE).title("Funny cat").status(status).build();
    }

    // ── review ──────────────────────────────────────────────────────────────────

    @Test
    void approved_createsOneApprovedNotificationForTheSuggester() {
        listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, true, "Funny cat", null));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(7L), eq("SUGGESTION_APPROVED"),
                eq("suggestion:5:SUGGESTION_APPROVED"), isNull(), isNull(), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("\"snippet\":\"Funny cat\"").doesNotContain("reason");
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void rejected_carriesReasonInPayload() {
        listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, false, "Funny cat", "COPYRIGHT"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(7L), eq("SUGGESTION_REJECTED"),
                eq("suggestion:5:SUGGESTION_REJECTED"), isNull(), isNull(), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("\"snippet\":\"Funny cat\"").contains("\"reason\":\"COPYRIGHT\"");
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void review_nullSnippet_storedAsEmpty() {
        listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, true, null, null));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(any(), anyString(), anyString(), any(), any(), any(), payload.capture());
        assertThat(payload.getValue()).contains("\"snippet\":\"\"");
    }

    @Test
    void review_deactivatedOrUnknownUser_noNotification() {
        when(userRepo.findById(8L)).thenReturn(Optional.of(user(8L, UserStatus.DEACTIVATED)));

        listener.onReviewed(new SuggestionReviewedEvent(5L, 8L, true, "x", null));
        listener.onReviewed(new SuggestionReviewedEvent(5L, 404L, true, "x", null));
        listener.onReviewed(new SuggestionReviewedEvent(5L, null, true, "x", null));

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void review_failureIsSwallowed() {
        when(userRepo.findById(7L)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onReviewed(new SuggestionReviewedEvent(5L, 7L, true, "x", null)))
                .doesNotThrowAnyException();
    }

    // ── live ────────────────────────────────────────────────────────────────────

    @Test
    void ingestOfApprovedSuggestionsDriveFile_createsLiveNotificationWithVideoId() {
        VideoImportJob job = VideoImportJob.builder().id(31L).build();
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.of(job));
        when(suggestionRepository.findFirstByImportJobId(31L)).thenReturn(Optional.of(suggestion(SuggestionStatus.APPROVED)));

        listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(eq(7L), eq(NotificationType.SUGGESTION_LIVE.name()),
                eq("suggestion:5:SUGGESTION_LIVE"), eq("501"), isNull(), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("\"snippet\":\"Funny cat\"");
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void ingestWithoutTitle_snippetIsTheUrl() {
        VideoSuggestion s = suggestion(SuggestionStatus.APPROVED);
        s.setTitle(null);
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.of(VideoImportJob.builder().id(31L).build()));
        when(suggestionRepository.findFirstByImportJobId(31L)).thenReturn(Optional.of(s));

        listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).upsertGrouped(any(), anyString(), anyString(), any(), any(), any(), payload.capture());
        assertThat(payload.getValue()).contains("https://youtu.be/dQw4w9WgXcQ");
    }

    @Test
    void ingestWithNoMatchingJob_doesNothing() {
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.empty());

        listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L));

        verifyNoInteractions(suggestionRepository);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void ingestWithJobButNoSuggestion_doesNothing() {
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.of(VideoImportJob.builder().id(31L).build()));
        when(suggestionRepository.findFirstByImportJobId(31L)).thenReturn(Optional.empty());

        listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L));

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void ingestOfNonApprovedSuggestion_doesNothing() {
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.of(VideoImportJob.builder().id(31L).build()));
        when(suggestionRepository.findFirstByImportJobId(31L)).thenReturn(Optional.of(suggestion(SuggestionStatus.REJECTED)));

        listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L));

        verify(notificationRepository, never()).upsertGrouped(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingest_lookupFailureIsSwallowed() {
        when(jobRepository.findFirstByDriveFileId(anyString())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L)))
                .doesNotThrowAnyException();
    }

    @Test
    void ingest_deliveryFailureIsSwallowed() {
        when(jobRepository.findFirstByDriveFileId("drive1")).thenReturn(Optional.of(VideoImportJob.builder().id(31L).build()));
        when(suggestionRepository.findFirstByImportJobId(31L)).thenReturn(Optional.of(suggestion(SuggestionStatus.APPROVED)));
        when(notificationRepository.upsertGrouped(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onVideoIngested(new VideoIngestedEvent("drive1", 501L)))
                .doesNotThrowAnyException();
        verify(publisher, never()).unreadChanged(org.mockito.ArgumentMatchers.anyLong());
    }
}
