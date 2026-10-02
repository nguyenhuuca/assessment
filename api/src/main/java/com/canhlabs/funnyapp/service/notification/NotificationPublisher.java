package com.canhlabs.funnyapp.service.notification;

/**
 * Pushes "your unread count changed" hints to the user's open clients.
 * Implementations: InMemorySsePublisher (single JVM); a LISTEN/NOTIFY one when scaled out (ADR-0017).
 */
public interface NotificationPublisher {
    void unreadChanged(long userId);
}
