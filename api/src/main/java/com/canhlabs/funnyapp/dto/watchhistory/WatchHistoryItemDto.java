package com.canhlabs.funnyapp.dto.watchhistory;

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
public class WatchHistoryItemDto {
    /** video_sources.id, same id as VideoDto.id. */
    private Long videoId;
    private String title;
    /** Thumbnail key (video_sources.source_id). */
    private String fileId;
    private long viewCount;
    private Instant watchedAt;
    private int positionSec;
    private Integer durationSec;
    private boolean completed;
}
