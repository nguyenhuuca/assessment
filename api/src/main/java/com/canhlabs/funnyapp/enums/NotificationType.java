package com.canhlabs.funnyapp.enums;

import java.util.EnumSet;
import java.util.Set;

public enum NotificationType {
    COMMENT_REPLY,
    COMMENT_REMOVED,
    SUGGESTION_APPROVED,
    SUGGESTION_REJECTED,
    SUGGESTION_LIVE,
    REPORT_CLOSED;

    /** Types produced by admin features; the notification dropdown lists them under its Admin tab. */
    public static final Set<NotificationType> ADMIN_TYPES = EnumSet.of(REPORT_CLOSED);
}
