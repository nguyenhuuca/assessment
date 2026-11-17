package com.canhlabs.funnyapp.service.backup;

import java.util.Collection;

/** Strips secrets and truncates free text before it reaches a log line or an email. */
public final class BackupRedactor {
    private static final String MASK = "***";
    private static final String ELLIPSIS = "…";

    private BackupRedactor() {
    }

    /** Replaces every non-blank secret with {@code ***}, then keeps the first {@code maxLen} chars. */
    public static String redact(String text, int maxLen, Collection<String> secrets) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && !secret.isBlank()) {
                    result = result.replace(secret, MASK);
                }
            }
        }
        if (maxLen >= 0 && result.length() > maxLen) {
            result = result.substring(0, maxLen) + ELLIPSIS;
        }
        return result;
    }
}
