package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.IncognitoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchSummaryDto;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import com.canhlabs.funnyapp.repo.WatchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchHistoryServiceImplTest {

    private static final Long USER = 7L;
    private static final Long VIDEO = 42L;

    @Mock
    private WatchHistoryRepository repo;

    @Mock
    private UserSettingsRepository settingsRepo;

    private final AppProperties props = new AppProperties();

    private IncognitoCache incognitoCache;
    private WatchHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        props.getWatchHistory().setMaxEntries(500);
        incognitoCache = new IncognitoCache();
        service = new WatchHistoryServiceImpl(repo, settingsRepo, props, incognitoCache);
    }

    private void incognito(boolean on) {
        UserSettings s = new UserSettings();
        s.setUserId(USER);
        s.setIncognitoEnabled(on);
        when(settingsRepo.findById(USER)).thenReturn(Optional.of(s));
    }

    // ---- recordWatch ----

    @Test
    void recordWatch_firstWatch_upsertsAndTrimsToConfiguredCap() {
        props.getWatchHistory().setMaxEntries(123);
        when(repo.upsertWatched(USER, VIDEO)).thenReturn(Optional.of(true));

        service.recordWatch(USER, VIDEO);

        verify(repo).upsertWatched(USER, VIDEO);
        verify(repo).trimToCap(USER, 123);
    }

    @Test
    void recordWatch_reWatch_updateOfExistingRow_noTrim() {
        when(repo.upsertWatched(USER, VIDEO)).thenReturn(Optional.of(false));

        service.recordWatch(USER, VIDEO);
        service.recordWatch(USER, VIDEO);

        verify(repo, times(2)).upsertWatched(USER, VIDEO);
        verify(repo, never()).trimToCap(anyLong(), anyInt());
    }

    @Test
    void recordWatch_hiddenOrUnknownVideo_nothingWritten_noTrim() {
        when(repo.upsertWatched(USER, VIDEO)).thenReturn(Optional.empty());

        service.recordWatch(USER, VIDEO);

        verify(repo, never()).trimToCap(anyLong(), anyInt());
    }

    @Test
    void recordWatch_incognito_writesNothing() {
        incognito(true);

        service.recordWatch(USER, VIDEO);

        verifyNoInteractions(repo);
    }

    @Test
    void recordWatch_incognitoOff_records() {
        incognito(false);
        when(repo.upsertWatched(USER, VIDEO)).thenReturn(Optional.of(true));

        service.recordWatch(USER, VIDEO);

        verify(repo).upsertWatched(USER, VIDEO);
    }

    // ---- saveProgress ----

    @Test
    void saveProgress_normalValues_updatesOnly() {
        service.saveProgress(USER, VIDEO, 30.7, 120.2);

        verify(repo).updateProgress(USER, VIDEO, 30, 120);
        verify(repo, never()).upsertWatched(anyLong(), anyLong());
    }

    @Test
    void saveProgress_incognito_isNoOp() {
        incognito(true);

        service.saveProgress(USER, VIDEO, 30.0, 120.0);

        verifyNoInteractions(repo);
    }

    @Test
    void saveProgress_durationClampedTo21600() {
        service.saveProgress(USER, VIDEO, 100.0, 999_999.0);

        verify(repo).updateProgress(USER, VIDEO, 100, 21_600);
    }

    @Test
    void saveProgress_zeroDurationClampedToOne() {
        service.saveProgress(USER, VIDEO, 0.0, 0.0);

        verify(repo).updateProgress(USER, VIDEO, 0, 1);
    }

    @Test
    void saveProgress_positionClampedToDuration() {
        service.saveProgress(USER, VIDEO, 500.0, 120.0);

        verify(repo).updateProgress(USER, VIDEO, 120, 120);
    }

    @Test
    void saveProgress_positionAboveClampedDuration() {
        service.saveProgress(USER, VIDEO, 50_000.0, 99_999.0);

        verify(repo).updateProgress(USER, VIDEO, 21_600, 21_600);
    }

    @Test
    void saveProgress_invalidValues_ignored() {
        service.saveProgress(USER, VIDEO, null, 120.0);
        service.saveProgress(USER, VIDEO, 10.0, null);
        service.saveProgress(USER, VIDEO, -1.0, 120.0);
        service.saveProgress(USER, VIDEO, 10.0, -5.0);
        service.saveProgress(USER, VIDEO, Double.NaN, 120.0);
        service.saveProgress(USER, VIDEO, 10.0, Double.POSITIVE_INFINITY);

        verifyNoInteractions(repo);
    }

    // ---- list ----

    private static WatchHistoryRepository.HistoryRow row(long id, int pos, Integer dur) {
        WatchHistoryRepository.HistoryRow r = mock(WatchHistoryRepository.HistoryRow.class);
        when(r.getVideoId()).thenReturn(id);
        when(r.getTitle()).thenReturn("T" + id);
        when(r.getFileId()).thenReturn("f" + id);
        when(r.getViewCount()).thenReturn(9L);
        when(r.getWatchedAt()).thenReturn(Instant.parse("2026-10-15T10:00:00Z"));
        when(r.getPositionSec()).thenReturn(pos);
        when(r.getDurationSec()).thenReturn(dur);
        return r;
    }

    @Test
    void list_mapsRowsAndCompletedFlag() {
        List<WatchHistoryRepository.HistoryRow> rows = List.of(row(1, 10, 100), row(2, 95, 100), row(3, 5, null));
        when(repo.findPage(eq(USER), isNull(), any())).thenReturn(new PageImpl<>(rows));

        Page<WatchHistoryItemDto> page = service.list(USER, null, 0, 20);

        assertThat(page.getContent()).extracting(WatchHistoryItemDto::getVideoId).containsExactly(1L, 2L, 3L);
        assertThat(page.getContent()).extracting(WatchHistoryItemDto::isCompleted).containsExactly(false, true, false);
        WatchHistoryItemDto first = page.getContent().get(0);
        assertThat(first.getTitle()).isEqualTo("T1");
        assertThat(first.getFileId()).isEqualTo("f1");
        assertThat(first.getViewCount()).isEqualTo(9L);
        assertThat(first.getWatchedAt()).isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));
        assertThat(first.getPositionSec()).isEqualTo(10);
        assertThat(first.getDurationSec()).isEqualTo(100);
    }

    private Pageable capturePageable(int page, int size) {
        when(repo.findPage(anyLong(), any(), any()))
                .thenReturn(Page.empty());
        service.list(USER, null, page, size);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findPage(eq(USER), isNull(), captor.capture());
        return captor.getValue();
    }

    @Test
    void list_sizeClampedToMax50() {
        assertThat(capturePageable(0, 500).getPageSize()).isEqualTo(50);
    }

    @Test
    void list_sizeClampedToMin1() {
        assertThat(capturePageable(0, 0).getPageSize()).isEqualTo(1);
    }

    @Test
    void list_negativePageBecomesZero() {
        assertThat(capturePageable(-3, 20).getPageNumber()).isZero();
    }

    private String capturePattern(String q) {
        when(repo.findPage(anyLong(), any(), any()))
                .thenReturn(Page.empty());
        service.list(USER, q, 0, 20);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repo).findPage(eq(USER), captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void list_queryIsTrimmedAndWrapped() {
        assertThat(capturePattern("  cat  ")).isEqualTo("%cat%");
    }

    @Test
    void list_blankQueryMeansNoFilter() {
        assertThat(capturePattern("   ")).isNull();
    }

    @Test
    void list_queryEscapesLikeWildcardsAndBackslash() {
        assertThat(capturePattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }

    @Test
    void list_queryTruncatedTo100Chars() {
        assertThat(capturePattern("a".repeat(150))).isEqualTo("%" + "a".repeat(100) + "%");
    }

    @Test
    void toLikePattern_nullIsNull() {
        assertThat(WatchHistoryServiceImpl.toLikePattern(null)).isNull();
    }

    // ---- completed boundaries ----

    @Test
    void isCompleted_boundaries() {
        assertThat(WatchHistoryServiceImpl.isCompleted(0, null)).isFalse();
        assertThat(WatchHistoryServiceImpl.isCompleted(89, 100)).isFalse();
        assertThat(WatchHistoryServiceImpl.isCompleted(90, 100)).isTrue();
        // short video: within 10 s of the end counts even below 90 %
        assertThat(WatchHistoryServiceImpl.isCompleted(2, 12)).isTrue();
        assertThat(WatchHistoryServiceImpl.isCompleted(5, 12)).isTrue();
        assertThat(WatchHistoryServiceImpl.isCompleted(500, 1200)).isFalse();
        assertThat(WatchHistoryServiceImpl.isCompleted(1080, 1200)).isTrue();
        assertThat(WatchHistoryServiceImpl.isCompleted(100, 100)).isTrue();
    }

    // ---- summary / remove / clear ----

    @Test
    void summary_mapsRowsWithCompleted() {
        WatchHistoryRepository.SummaryRow a = mock(WatchHistoryRepository.SummaryRow.class);
        when(a.getVideoId()).thenReturn(1L);
        when(a.getPositionSec()).thenReturn(95);
        when(a.getDurationSec()).thenReturn(100);
        WatchHistoryRepository.SummaryRow b = mock(WatchHistoryRepository.SummaryRow.class);
        when(b.getVideoId()).thenReturn(2L);
        when(b.getPositionSec()).thenReturn(3);
        when(b.getDurationSec()).thenReturn(null);
        when(repo.findSummary(USER, 500)).thenReturn(List.of(a, b));

        List<WatchSummaryDto> result = service.summary(USER);

        assertThat(result).extracting(WatchSummaryDto::getVideoId).containsExactly(1L, 2L);
        assertThat(result).extracting(WatchSummaryDto::isCompleted).containsExactly(true, false);
        assertThat(result.get(1).getDurationSec()).isNull();
        assertThat(result.get(0).getPositionSec()).isEqualTo(95);
    }

    @Test
    void remove_deletesOnlyThatUsersEntry() {
        service.remove(USER, VIDEO);

        verify(repo).deleteOne(USER, VIDEO);
    }

    @Test
    void clear_deletesAllOfThatUser() {
        service.clear(USER);

        verify(repo).deleteAll(USER);
    }

    // ---- incognito cache ----

    @Test
    void progressPings_readTheSettingsOnce() {
        incognito(false);

        for (int i = 0; i < 10; i++) {
            service.saveProgress(USER, VIDEO, 30.0, 120.0);
        }

        verify(settingsRepo, times(1)).findById(USER);
        verify(repo, times(10)).updateProgress(USER, VIDEO, 30, 120);
    }

    @Test
    void evict_makesTheNextCallReloadTheSetting() {
        incognito(false);
        service.saveProgress(USER, VIDEO, 30.0, 120.0);
        incognito(true);

        incognitoCache.evict(USER);
        service.saveProgress(USER, VIDEO, 30.0, 120.0);

        verify(settingsRepo, times(2)).findById(USER);
        verify(repo, times(1)).updateProgress(USER, VIDEO, 30, 120);
    }
}
