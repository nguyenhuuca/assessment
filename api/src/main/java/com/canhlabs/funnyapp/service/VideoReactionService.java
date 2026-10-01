package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.enums.ReactionType;

public interface VideoReactionService {

    /** Public: counts for everyone, myReaction only when a user is authenticated. */
    ReactionSummaryDto getSummary(Long videoId);

    /** Sets (or switches) the current user's reaction. Requires authentication. */
    ReactionSummaryDto react(Long videoId, ReactionType type);

    /** Clears the current user's reaction (idempotent). Requires authentication. */
    ReactionSummaryDto removeReaction(Long videoId);
}
