package com.canhlabs.funnyapp.client;

/** Subset of the yt-dlp {@code -J} output used by the import. */
public record VideoMetadata(String id, String title, Long durationSec) {
}
