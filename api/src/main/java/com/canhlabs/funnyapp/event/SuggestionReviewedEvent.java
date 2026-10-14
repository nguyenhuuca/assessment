package com.canhlabs.funnyapp.event;

/**
 * Published after an admin approved or rejected a suggestion. snippet is the suggestion title (or its URL),
 * reason the reject reason code (rejections only).
 */
public record SuggestionReviewedEvent(Long suggestionId, Long userId, boolean approved, String snippet, String reason) {
}
