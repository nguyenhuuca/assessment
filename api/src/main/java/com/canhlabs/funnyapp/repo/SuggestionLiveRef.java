package com.canhlabs.funnyapp.repo;

/**
 * Maps a suggestion to the video_sources row created from its import job.
 * fileId is video_sources.source_id (the Drive file id), used for the thumbnail URL.
 */
public record SuggestionLiveRef(Long suggestionId, Long videoSourceId, String fileId) {
}
