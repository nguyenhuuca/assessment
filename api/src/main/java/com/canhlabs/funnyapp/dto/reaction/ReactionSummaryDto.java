package com.canhlabs.funnyapp.dto.reaction;

import com.canhlabs.funnyapp.enums.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionSummaryDto {
    private Long videoId;
    private long likeCount;
    private long dislikeCount;
    /** The caller's own reaction; null for guests or when none. */
    private ReactionType myReaction;
}
