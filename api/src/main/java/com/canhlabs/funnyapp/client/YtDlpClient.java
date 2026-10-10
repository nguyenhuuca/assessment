package com.canhlabs.funnyapp.client;

import java.nio.file.Path;
import java.util.Optional;

public interface YtDlpClient {

    /** Receives raw byte counters; {@code totalBytes} is 0 when unknown. */
    @FunctionalInterface
    interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    /** Runs {@code yt-dlp -J --skip-download}; throws ImportException with a mapped error code. */
    VideoMetadata fetchMetadata(String url);

    /**
     * Downloads into {@code jobDir} (created if missing) and returns the produced media file.
     * Throws ImportException (UNSUPPORTED_URL, LOGIN_REQUIRED, TOO_LARGE, EXTRACTOR_ERROR, TIMEOUT,
     * CANCELLED, NOT_INSTALLED).
     */
    Path download(String url, Path jobDir, ProgressListener listener, CancelToken cancel);

    /** yt-dlp version, empty when the binary cannot be started. */
    Optional<String> version();
}
