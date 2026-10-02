package com.canhlabs.funnyapp.service.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a server-sent-events stream for a user.
 */
public interface NotificationStream {
    /**
     * Registers a new stream for the user and immediately sends the current unread count.
     */
    SseEmitter register(long userId);
}
