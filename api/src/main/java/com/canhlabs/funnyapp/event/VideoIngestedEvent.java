package com.canhlabs.funnyapp.event;

/**
 * Published after the cron saved a new video_sources row for a Drive file. Publish-only: it must never
 * change or fail the ingest.
 */
public record VideoIngestedEvent(String driveFileId, Long videoSourceId) {
}
