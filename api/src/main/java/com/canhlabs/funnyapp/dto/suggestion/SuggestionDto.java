package com.canhlabs.funnyapp.dto.suggestion;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDto {
    private Long id;
    private String sourceUrl;
    private ImportPlatform platform;
    private String title;
    private String note;
    private SuggestionStatus status;
    /** APPROVED and its import job's Drive file is in video_sources. */
    private boolean live;
    /** video_sources.id, same id as VideoDto.id. */
    private Long liveVideoId;
    private SuggestionRejectReason rejectReason;
    private String rejectNote;
    private Instant createdAt;
    private Instant reviewedAt;
    /** Masked (NotificationText.actorDisplay); admin endpoints only. */
    private String suggesterName;
    /** Admin endpoints only. */
    private Long importJobId;
}
