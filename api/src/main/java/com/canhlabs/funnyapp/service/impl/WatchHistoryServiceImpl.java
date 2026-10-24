package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.IncognitoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchSummaryDto;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import com.canhlabs.funnyapp.repo.WatchHistoryRepository;
import com.canhlabs.funnyapp.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_DURATION_SEC = 21_600;
    static final int SUMMARY_LIMIT = 500;

    private final WatchHistoryRepository watchHistoryRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AppProperties appProperties;
    private final IncognitoCache incognitoCache;

    @Override
    @Transactional
    public void recordWatch(Long userId, Long videoId) {
        if (isIncognito(userId)) {
            return;
        }
        // trim only when the entry is new: moving an existing one to the top cannot exceed the cap
        if (watchHistoryRepository.upsertWatched(userId, videoId).orElse(false)) {
            watchHistoryRepository.trimToCap(userId, appProperties.getWatchHistory().getMaxEntries());
        }
    }

    @Override
    @Transactional
    public void saveProgress(Long userId, Long videoId, Double positionSec, Double durationSec) {
        if (!isUsable(positionSec) || !isUsable(durationSec)) {
            return;
        }
        if (isIncognito(userId)) {
            return;
        }
        int dur = (int) Math.min(MAX_DURATION_SEC, Math.max(1, Math.floor(durationSec)));
        int pos = (int) Math.min(dur, Math.floor(positionSec));
        watchHistoryRepository.updateProgress(userId, videoId, pos, dur);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WatchHistoryItemDto> list(Long userId, String q, int page, int size) {
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int safePage = Math.max(0, page);
        return watchHistoryRepository.findPage(userId, toLikePattern(q), PageRequest.of(safePage, safeSize))
                .map(WatchHistoryServiceImpl::toItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchSummaryDto> summary(Long userId) {
        return watchHistoryRepository.findSummary(userId, SUMMARY_LIMIT).stream()
                .map(r -> WatchSummaryDto.builder()
                        .videoId(r.getVideoId())
                        .positionSec(r.getPositionSec())
                        .durationSec(r.getDurationSec())
                        .completed(isCompleted(r.getPositionSec(), r.getDurationSec()))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void remove(Long userId, Long videoId) {
        watchHistoryRepository.deleteOne(userId, videoId);
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        watchHistoryRepository.deleteAll(userId);
    }

    static boolean isCompleted(int positionSec, Integer durationSec) {
        return durationSec != null && (positionSec >= 0.9 * durationSec || durationSec - positionSec <= 10);
    }

    /** Trimmed, at most 100 chars, LIKE wildcards escaped; null when there is nothing to filter on. */
    static String toLikePattern(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.strip();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        if (trimmed.isBlank()) {
            return null;
        }
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean isUsable(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private boolean isIncognito(Long userId) {
        return incognitoCache.get(userId, () ->
                userSettingsRepository.findById(userId).map(UserSettings::isIncognitoEnabled).orElse(false));
    }

    private static WatchHistoryItemDto toItem(WatchHistoryRepository.HistoryRow r) {
        return WatchHistoryItemDto.builder()
                .videoId(r.getVideoId())
                .title(r.getTitle())
                .fileId(r.getFileId())
                .viewCount(r.getViewCount())
                .watchedAt(r.getWatchedAt())
                .positionSec(r.getPositionSec())
                .durationSec(r.getDurationSec())
                .completed(isCompleted(r.getPositionSec(), r.getDurationSec()))
                .build();
    }
}
