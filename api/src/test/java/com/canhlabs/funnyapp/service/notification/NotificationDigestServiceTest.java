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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDigestServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepo userRepo;
    @Mock MailService mailService;
    @Mock EmailCacheLimiter emailLimiter;

    AppProperties props;
    NotificationDigestService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.setDomain("https://app.example.com");
        props.getEmailSetting().setMaxDailyEmails(200);
        service = new NotificationDigestService(notificationRepository, userRepo, mailService, emailLimiter, props);
    }

    private static User user(long id, String email, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setUserName(email);
        u.setStatus(status);
        return u;
    }

    private static Notification reply(String actor, int count, String snippet) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setType(NotificationType.COMMENT_REPLY);
        n.setActorDisplay(actor);
        n.setActorCount(count);
        n.setPayload(Map.of("snippet", snippet));
        return n;
    }

    private static Notification removed(String reason) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setPayload(Map.of("reason", reason));
        return n;
    }

    // ── digest ────────────────────────────────────────────────────────────────

    @Test
    void sendDigests_oneEmailPerUser_incrementsBudgetAndSetsEmailedAt() {
        Notification a = reply("bob", 3, "haha same");
        Notification b = removed("SPAM");
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(userRepo.findAllById(1L)).thenReturn(user(1L, "me@x.com", UserStatus.ACTIVE));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(a, b));

        int sent = service.sendDigests();

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(mailService, emailLimiter, notificationRepository);
        order.verify(mailService).sendSimpleMail(eq("me@x.com"), subject.capture(), body.capture());
        order.verify(emailLimiter).incrementDailyCount(any(LocalDate.class));
        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        order.verify(notificationRepository).markEmailed(ids.capture(), any(Instant.class));
        assertThat(ids.getValue()).containsExactly(a.getId(), b.getId());
        assertThat(subject.getValue()).contains("2");
        assertThat(body.getValue())
                .contains("bob và 2 người khác đã trả lời bình luận của bạn: \"haha same\"")
                .contains("Bình luận của bạn đã bị gỡ (SPAM)")
                .contains("https://app.example.com")
                .contains("Tắt email thông báo trong Cài đặt")
                .doesNotContain("khác.\n");
    }

    @Test
    void sendDigests_listsAtMostFiveAndSummarizesRest() {
        List<Notification> many = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> reply("u" + i, 1, "s" + i)).toList();
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(userRepo.findAllById(1L)).thenReturn(user(1L, "me@x.com", UserStatus.ACTIVE));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(1L)).thenReturn(many);

        service.sendDigests();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSimpleMail(eq("me@x.com"), anyString(), body.capture());
        assertThat(body.getValue()).contains("u4 đã trả lời").doesNotContain("u5 đã trả lời")
                .contains("và 3 thông báo khác");
        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).markEmailed(ids.capture(), any(Instant.class));
        assertThat(ids.getValue()).hasSize(8); // everything covered by the digest is marked emailed
    }

    @Test
    void sendDigests_noOptedInUsers_sendsNothing() {
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of());

        assertThat(service.sendDigests()).isZero();

        verifyNoInteractions(mailService, userRepo, emailLimiter);
    }

    @Test
    void sendDigests_stopsWhenHalfOfDailyBudgetIsUsed() {
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L, 2L, 3L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(98, 99, 100);
        when(userRepo.findAllById(any(Long.class))).thenAnswer(inv ->
                user((Long) inv.getArgument(0), "u" + inv.getArgument(0) + "@x.com", UserStatus.ACTIVE));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of(reply("bob", 1, "s")));

        int sent = service.sendDigests();

        assertThat(sent).isEqualTo(2); // counts 98 and 99 are below 100 (= 50% of 200); 100 stops the run
        verify(mailService, times(2)).sendSimpleMail(anyString(), anyString(), anyString());
        verify(mailService, never()).sendSimpleMail(eq("u3@x.com"), anyString(), anyString());
        verify(emailLimiter, times(2)).incrementDailyCount(any(LocalDate.class));
        verify(userRepo, never()).findAllById(3L);
    }

    @Test
    void sendDigests_budgetAlreadyUsedAtStart_sendsNothing() {
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(100);

        assertThat(service.sendDigests()).isZero();

        verifyNoInteractions(mailService);
        verify(notificationRepository, never()).markEmailed(any(), any());
    }

    @Test
    void sendDigests_zeroConfiguredBudget_sendsNothing() {
        props.getEmailSetting().setMaxDailyEmails(0);
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);

        assertThat(service.sendDigests()).isZero();

        verifyNoInteractions(mailService);
    }

    @Test
    void sendDigests_skipsDeactivatedAndUnknownUsers() {
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L, 2L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(userRepo.findAllById(1L)).thenReturn(user(1L, "gone@x.com", UserStatus.DEACTIVATED));
        when(userRepo.findAllById(2L)).thenReturn(null);

        assertThat(service.sendDigests()).isZero();

        verifyNoInteractions(mailService);
        verify(notificationRepository, never()).markEmailed(any(), any());
    }

    @Test
    void sendDigests_nothingPendingAnymore_sendsNothing() {
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(userRepo.findAllById(1L)).thenReturn(user(1L, "me@x.com", UserStatus.ACTIVE));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of());

        assertThat(service.sendDigests()).isZero();

        verifyNoInteractions(mailService);
    }

    @Test
    void sendDigests_mailFailure_leavesEmailedAtUnsetAndContinues() {
        Notification first = reply("a", 1, "s");
        Notification second = reply("b", 1, "s");
        when(notificationRepository.findUserIdsForDigest()).thenReturn(List.of(1L, 2L));
        when(emailLimiter.getDailyCount(any(LocalDate.class))).thenReturn(0);
        when(userRepo.findAllById(1L)).thenReturn(user(1L, "one@x.com", UserStatus.ACTIVE));
        when(userRepo.findAllById(2L)).thenReturn(user(2L, "two@x.com", UserStatus.ACTIVE));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(1L)).thenReturn(List.of(first));
        when(notificationRepository.findByUserIdAndReadAtIsNullAndEmailedAtIsNullOrderByUpdatedAtDesc(2L)).thenReturn(List.of(second));
        doThrow(new IllegalStateException("smtp down")).when(mailService).sendSimpleMail(eq("one@x.com"), anyString(), anyString());

        int sent = service.sendDigests();

        assertThat(sent).isEqualTo(1);
        verify(emailLimiter, times(1)).incrementDailyCount(any(LocalDate.class)); // failed send not counted
        verify(notificationRepository, never()).markEmailed(eq(List.of(first.getId())), any());
        verify(notificationRepository).markEmailed(eq(List.of(second.getId())), any(Instant.class));
    }

    // ── retention ─────────────────────────────────────────────────────────────

    @Test
    void purgeOldRead_usesNinetyDayCutoff() {
        Instant now = Instant.parse("2026-10-02T00:00:00Z");
        when(notificationRepository.deleteReadOlderThan(now.minus(Duration.ofDays(90)))).thenReturn(12);

        assertThat(service.purgeOldRead(now)).isEqualTo(12);

        verify(notificationRepository).deleteReadOlderThan(Instant.parse("2026-07-04T00:00:00Z"));
    }
}
