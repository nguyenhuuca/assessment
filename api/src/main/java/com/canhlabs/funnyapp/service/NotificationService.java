package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Inbox of the currently authenticated user. Every operation is scoped to that user.
 */
public interface NotificationService {
    /** category null = every type; ADMIN / GENERAL split by {@code NotificationType.ADMIN_TYPES}. */
    Page<NotificationDto> list(NotificationCategory category, Pageable pageable);

    long unreadCount();

    /**
     * Marks one notification read. Throws 404 when it does not exist or belongs to someone else.
     */
    void markRead(UUID id);

    void markAllRead();
}
