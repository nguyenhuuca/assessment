package com.canhlabs.funnyapp.exception;

import java.util.Map;

/** Error codes stored in {@code video_import_jobs.error_code} with their fixed, client-safe messages. */
public final class ImportErrorCode {
    public static final String UNSUPPORTED_URL = "UNSUPPORTED_URL";
    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String TOO_LARGE = "TOO_LARGE";
    public static final String EXTRACTOR_ERROR = "EXTRACTOR_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CANCELLED = "CANCELLED";
    public static final String NOT_INSTALLED = "NOT_INSTALLED";
    public static final String DRIVE_NOT_CONFIGURED = "DRIVE_NOT_CONFIGURED";
    public static final String DRIVE_AUTH_FAILED = "DRIVE_AUTH_FAILED";
    public static final String DRIVE_UPLOAD_FAILED = "DRIVE_UPLOAD_FAILED";
    public static final String DISK_LOW = "DISK_LOW";
    public static final String INTERRUPTED = "INTERRUPTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry(UNSUPPORTED_URL, "This URL is not supported"),
            Map.entry(LOGIN_REQUIRED, "The video requires login or is private"),
            Map.entry(TOO_LARGE, "The video exceeds the maximum allowed file size"),
            Map.entry(EXTRACTOR_ERROR, "The video could not be downloaded"),
            Map.entry(TIMEOUT, "The operation timed out"),
            Map.entry(CANCELLED, "Cancelled by admin"),
            Map.entry(NOT_INSTALLED, "yt-dlp is not installed on the server"),
            Map.entry(DRIVE_NOT_CONFIGURED, "Google Drive upload is not configured"),
            Map.entry(DRIVE_AUTH_FAILED, "Google Drive authorization failed"),
            Map.entry(DRIVE_UPLOAD_FAILED, "Uploading to Google Drive failed"),
            Map.entry(DISK_LOW, "Not enough free disk space on the server"),
            Map.entry(INTERRUPTED, "Import was interrupted by a restart"),
            Map.entry(INTERNAL_ERROR, "Unexpected error while importing"));

    private ImportErrorCode() {
    }

    public static String messageFor(String code) {
        return MESSAGES.getOrDefault(code, MESSAGES.get(INTERNAL_ERROR));
    }
}
