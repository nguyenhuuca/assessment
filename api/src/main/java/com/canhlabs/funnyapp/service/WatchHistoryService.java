package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchSummaryDto;
import org.springframework.data.domain.Page;

import java.util.List;

public interface WatchHistoryService {

    /** Records or refreshes the entry; no-op in incognito and for unknown or hidden videos. */
    void recordWatch(Long userId, Long videoId);

    /** Updates the position of an existing entry; never inserts; no-op in incognito. */
    void saveProgress(Long userId, Long videoId, Double positionSec, Double durationSec);

    Page<WatchHistoryItemDto> list(Long userId, String q, int page, int size);

    List<WatchSummaryDto> summary(Long userId);

    void remove(Long userId, Long videoId);

    void clear(Long userId);
}
