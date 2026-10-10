package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.client.CancelToken;

import java.nio.file.Path;

/** Uploads a finished download into the shared Drive folder (ADR-0019 D2). */
public interface DriveUploader {

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(long uploadedBytes, long totalBytes);
    }

    /** False when no upload credential is configured; {@link #upload} then fails with DRIVE_NOT_CONFIGURED. */
    boolean isConfigured();

    /**
     * Resumable upload into {@code AppConstant.FOLDER_ID}; returns the Drive file id.
     * Throws ImportException (DRIVE_NOT_CONFIGURED, DRIVE_AUTH_FAILED, DRIVE_UPLOAD_FAILED, CANCELLED).
     */
    String upload(Path file, String fileName, String importJobId, ProgressListener listener, CancelToken cancel);
}
