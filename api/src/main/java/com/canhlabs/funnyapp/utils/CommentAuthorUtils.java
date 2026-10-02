package com.canhlabs.funnyapp.utils;

public final class CommentAuthorUtils {
    private CommentAuthorUtils() {
    }

    /** Stable anonymous alias for a guest, derived from the guest token hash. */
    public static String guestAlias(String guestTokenHash) {
        int hash = AppUtils.hashCode(guestTokenHash);
        return "Anonymous" + (Math.abs(hash % 1000) + 1);
    }
}
