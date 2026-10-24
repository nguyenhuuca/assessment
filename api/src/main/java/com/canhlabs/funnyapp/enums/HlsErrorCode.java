package com.canhlabs.funnyapp.enums;

/** Reasons an HLS job ends in ERROR (BR-9); stored by name in {@code video_sources.hls_error}. */
public enum HlsErrorCode {
    SOURCE_MISSING,
    FFMPEG_NOT_FOUND,
    UNSUPPORTED_CODEC,
    DISK_FULL,
    TIMEOUT,
    FFMPEG_FAILED,
    OUTPUT_MISSING
}
