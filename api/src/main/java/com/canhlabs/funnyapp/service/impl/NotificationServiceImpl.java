package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.Notification;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.service.NotificationService;
import com.canhlabs.funnyapp.service.notification.NotificationPublisher;
import com.canhlabs.funnyapp.utils.AppUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationPublisher publisher;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> list(Pageable pageable) {
        return repository.findByUserIdOrderByUpdatedAtDesc(currentUserId(), pageable).map(NotificationServiceImpl::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndReadAtIsNull(currentUserId());
    }

    // Deliberately not @Transactional: each repository update commits before the publisher recounts unread
    @Override
    public void markRead(UUID id) {
        Long userId = currentUserId();
        // Owner-scoped lookup: another user's id is indistinguishable from a missing one (CWE-639)
        repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4041, "Notification not found"));
        repository.markRead(id, userId, Instant.now());
        publisher.unreadChanged(userId);
    }

    @Override
    public void markAllRead() {
        Long userId = currentUserId();
        repository.markAllRead(userId, Instant.now());
        publisher.unreadChanged(userId);
    }

    private static Long currentUserId() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw error(HttpStatus.UNAUTHORIZED, 4011, "Authentication required");
        }
        return user.getId();
    }

    static NotificationDto toDto(Notification n) {
        Map<String, Object> payload = n.getPayload() == null ? Map.of() : n.getPayload();
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .videoId(n.getVideoId())
                .commentId(n.getCommentId())
                .actorDisplay(n.getActorDisplay())
                .actorCount(n.getActorCount())
                .snippet(asString(payload.get("snippet")))
                .reason(asString(payload.get("reason")))
                .read(n.getReadAt() != null)
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }
}
