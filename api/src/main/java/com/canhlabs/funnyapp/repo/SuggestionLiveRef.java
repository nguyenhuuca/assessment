package com.canhlabs.funnyapp.repo;

/** Maps a suggestion to the video_sources row created from its import job. */
public record SuggestionLiveRef(Long suggestionId, Long videoSourceId) {
}
