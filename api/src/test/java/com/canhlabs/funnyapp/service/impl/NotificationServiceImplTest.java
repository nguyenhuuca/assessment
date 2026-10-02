package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.Notification;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.service.notification.NotificationPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationRepository repository;
    @Mock NotificationPublisher publisher;
    @InjectMocks NotificationServiceImpl service;

    @BeforeEach
    void authenticate() {
        UserDetailDto user = UserDetailDto.builder().id(7L).email("me@x.com").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("me@x.com", null, List.of());
        auth.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Notification reply(boolean read) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setUserId(7L);
        n.setType(NotificationType.COMMENT_REPLY);
        n.setVideoId("22");
        n.setCommentId(UUID.randomUUID());
        n.setActorDisplay("bob");
        n.setActorCount(3);
        n.setPayload(Map.of("snippet", "haha same here"));
        n.setReadAt(read ? Instant.now() : null);
        n.setUpdatedAt(Instant.parse("2026-10-02T10:00:00Z"));
        return n;
    }

    @Test
    void list_mapsRowsToDtoForCurrentUserOnly() {
        Notification n = reply(false);
        PageRequest pageable = PageRequest.of(1, 20);
        when(repository.findByUserIdOrderByUpdatedAtDesc(7L, pageable)).thenReturn(new PageImpl<>(List.of(n), pageable, 21));

        Page<NotificationDto> page = service.list(pageable);

        assertThat(page.getTotalElements()).isEqualTo(21);
        NotificationDto dto = page.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(n.getId());
        assertThat(dto.getType()).isEqualTo(NotificationType.COMMENT_REPLY);
        assertThat(dto.getVideoId()).isEqualTo("22");
        assertThat(dto.getCommentId()).isEqualTo(n.getCommentId());
        assertThat(dto.getActorDisplay()).isEqualTo("bob");
        assertThat(dto.getActorCount()).isEqualTo(3);
        assertThat(dto.getSnippet()).isEqualTo("haha same here");
        assertThat(dto.getReason()).isNull();
        assertThat(dto.isRead()).isFalse();
        assertThat(dto.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-02T10:00:00Z"));
    }

    @Test
    void list_removedRow_exposesReasonOnlyAndReadFlag() {
        Notification n = reply(true);
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setPayload(Map.of("reason", "SPAM"));
        when(repository.findByUserIdOrderByUpdatedAtDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of(n)));

        NotificationDto dto = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(dto.getReason()).isEqualTo("SPAM");
        assertThat(dto.getSnippet()).isNull();
        assertThat(dto.isRead()).isTrue();
    }

    @Test
    void list_nullPayload_isTolerated() {
        Notification n = reply(false);
        n.setPayload(null);
        when(repository.findByUserIdOrderByUpdatedAtDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of(n)));

        assertThat(service.list(PageRequest.of(0, 20)).getContent().get(0).getSnippet()).isNull();
    }

    @Test
    void unreadCount_countsForCurrentUser() {
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(4L);

        assertThat(service.unreadCount()).isEqualTo(4L);
    }

    @Test
    void markRead_ownNotification_marksAndPublishes() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, 7L)).thenReturn(Optional.of(reply(false)));

        service.markRead(id);

        verify(repository).markRead(eq(id), eq(7L), any(Instant.class));
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void markRead_otherUsersNotification_is404AndNothingChanges() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(id))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(repository, never()).markRead(any(), any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void markAllRead_marksForCurrentUserAndPublishes() {
        service.markAllRead();

        verify(repository).markAllRead(eq(7L), any(Instant.class));
        verify(publisher).unreadChanged(7L);
    }

    @Test
    void noAuthenticatedUser_is401() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.unreadCount())
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(repository);
    }
}
