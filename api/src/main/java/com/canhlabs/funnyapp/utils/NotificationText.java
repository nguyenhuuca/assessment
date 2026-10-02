package com.canhlabs.funnyapp.utils;

/**
 * Text helpers for notification content: never expose a full email, keep snippets short.
 */
public final class NotificationText {

    public static final int SNIPPET_MAX = 140;

    private NotificationText() {
    }

    /**
     * Whitespace-collapsed snippet of at most {@value #SNIPPET_MAX} characters (ellipsis included).
     */
    public static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String collapsed = content.trim().replaceAll("\\s+", " ");
        if (collapsed.length() <= SNIPPET_MAX) {
            return collapsed;
        }
        return collapsed.substring(0, SNIPPET_MAX - 1).stripTrailing() + "…";
    }

    /**
     * Local part of an email address ("bob@x.com" -> "bob"); never the full address.
     */
    public static String actorDisplay(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        String local = at > 0 ? trimmed.substring(0, at) : trimmed;
        return local.length() > 100 ? local.substring(0, 100) : local;
    }
}
