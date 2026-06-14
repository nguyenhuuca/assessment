package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.cache.CacheStat;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSchedulerTest {
    @Mock StreamVideoService streamVideoService;
    @Mock StatsCache statsCache;
    @Mock VideoAccessService videoAccessService;
    @Mock VideoStorageService videoStorageService;
    @Mock FfmpegService ffmpegService;
    @Mock VideoSourceRepository videoSourceRepository;
    @Mock AppProperties appProps;
    @Mock YouTubeVideoService youTubeVideoService;

    @InjectMocks
    AppScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "regenerateThumbnailsEnabled", false);
    }

    // ── run() ──────────────────────────────────────────────────────────────────

    @Test
    void run_whenDisabled_doesNotTouchRepository() {
        scheduler.run(null);
        verifyNoInteractions(videoSourceRepository);
    }

    @Test
    void run_whenEnabled_delegatesToRegenerateThumbnails() {
        ReflectionTestUtils.setField(scheduler, "regenerateThumbnailsEnabled", true);
        when(videoSourceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        scheduler.run(null);

        verify(videoSourceRepository).findAllByOrderByCreatedAtDesc();
    }

    // ── regenerateThumbnails() ─────────────────────────────────────────────────

    @Test
    void regenerateThumbnails_emptySourceList_doesNothing() {
        when(videoSourceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        scheduler.regenerateThumbnails();

        verifyNoInteractions(ffmpegService);
        verify(videoSourceRepository, never()).save(any());
    }

    @Test
    void regenerateThumbnails_localFileMissing_skipsSource() {
        VideoSource source = source("no-such-file-id");
        when(videoSourceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(source));

        scheduler.regenerateThumbnails();

        verifyNoInteractions(ffmpegService);
        verify(videoSourceRepository, never()).save(any());
    }

    @Disabled
    @Test
    void regenerateThumbnails_localFileExists_generatesThumbnailAndUpdatesDb() throws Exception {
        String sourceId = "regen-test-source";
        File localFile = createTempVideoFile(sourceId);
        try {
            VideoSource source = source(sourceId);
            when(videoSourceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(source));
            when(appProps.getImageStoragePath()).thenReturn("/var/images");
            when(appProps.getImageUrl()).thenReturn("https://images.example.com");
            doNothing().when(ffmpegService).generateThumbnail(anyString(), anyString());
            when(videoSourceRepository.save(any())).thenReturn(source);

            scheduler.regenerateThumbnails();

            verify(ffmpegService).generateThumbnail(
                    localFile.getAbsolutePath(),
                    "/var/images/thumbnails/" + sourceId + ".jpg");
            verify(videoSourceRepository).save(
                    argThat(s -> ("https://images.example.com/" + sourceId + ".jpg")
                            .equals(s.getThumbnailPath())));
        } finally {
            localFile.delete();
        }
    }

    @Test
    void regenerateThumbnails_ffmpegFailure_continuesWithRemainingSourcesAndSavesSuccessful() throws Exception {
        String failId = "fail-source";
        String okId = "ok-source";
        File failFile = createTempVideoFile(failId);
        File okFile = createTempVideoFile(okId);
        try {
            when(videoSourceRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(source(failId), source(okId)));
            when(appProps.getImageStoragePath()).thenReturn("/var/images");
            when(appProps.getImageUrl()).thenReturn("https://images.example.com");
            doThrow(new RuntimeException("ffmpeg error"))
                    .when(ffmpegService).generateThumbnail(contains(failId), anyString());
            doNothing().when(ffmpegService).generateThumbnail(contains(okId), anyString());
            when(videoSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            scheduler.regenerateThumbnails();

            verify(ffmpegService, times(2)).generateThumbnail(anyString(), anyString());
            verify(videoSourceRepository, times(1)).save(
                    argThat(s -> s.getSourceId().equals(okId)));
            verify(videoSourceRepository, never()).save(
                    argThat(s -> s.getSourceId().equals(failId)));
        } finally {
            failFile.delete();
            okFile.delete();
        }
    }

    @Test
    void regenerateThumbnails_mixOfLocalAndMissing_onlyProcessesExisting() throws Exception {
        String existingId = "existing-source";
        String missingId = "missing-source";
        File existingFile = createTempVideoFile(existingId);
        try {
            when(videoSourceRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(source(existingId), source(missingId)));
            when(appProps.getImageStoragePath()).thenReturn("/var/images");
            when(appProps.getImageUrl()).thenReturn("https://images.example.com");
            doNothing().when(ffmpegService).generateThumbnail(anyString(), anyString());
            when(videoSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            scheduler.regenerateThumbnails();

            verify(ffmpegService, times(1)).generateThumbnail(anyString(), anyString());
            verify(videoSourceRepository, times(1)).save(any());
        } finally {
            existingFile.delete();
        }
    }

    // ── scheduleMakePoem() ────────────────────────────────────────────────────

    @Test
    void scheduleMakePoem_happyPath_callsUpdateDesc() {
        doNothing().when(streamVideoService).updateDesc();

        scheduler.scheduleMakePoem();

        verify(streamVideoService).updateDesc();
    }

    @Test
    void scheduleMakePoem_whenUpdateDescThrows_exceptionIsCaughtAndNotRethrown() {
        doThrow(new RuntimeException("service failure")).when(streamVideoService).updateDesc();

        // Must not throw
        scheduler.scheduleMakePoem();

        verify(streamVideoService).updateDesc();
    }

    // ── logStats() ────────────────────────────────────────────────────────────

    @Test
    void logStats_emptyFileStats_logsHitsAndMissesOnly() {
        when(statsCache.getTotalHits()).thenReturn(10L);
        when(statsCache.getTotalMisses()).thenReturn(2L);
        when(statsCache.getFileStats()).thenReturn(Map.of());

        scheduler.logStats();

        verify(statsCache).getTotalHits();
        verify(statsCache).getTotalMisses();
        verify(statsCache).getFileStats();
        verify(statsCache, never()).calculateRatio();
    }

    @Test
    void logStats_nonEmptyFileStats_logsEachEntryAndCallsCalculateRatio() {
        CacheStat stat = CacheStat.builder().hits(5L).misses(1L).build();
        when(statsCache.getTotalHits()).thenReturn(5L);
        when(statsCache.getTotalMisses()).thenReturn(1L);
        when(statsCache.getFileStats()).thenReturn(Map.of("video-1", stat));
        when(statsCache.calculateRatio()).thenReturn(83L);

        scheduler.logStats();

        verify(statsCache).getTotalHits();
        verify(statsCache).getTotalMisses();
        verify(statsCache).getFileStats();
        verify(statsCache).calculateRatio();
    }

    @Test
    void logStats_multipleFileStats_calculateRatioCalledForEachEntry() {
        CacheStat stat1 = CacheStat.builder().hits(3L).misses(0L).build();
        CacheStat stat2 = CacheStat.builder().hits(7L).misses(2L).build();
        when(statsCache.getTotalHits()).thenReturn(10L);
        when(statsCache.getTotalMisses()).thenReturn(2L);
        when(statsCache.getFileStats()).thenReturn(Map.of("vid-a", stat1, "vid-b", stat2));
        when(statsCache.calculateRatio()).thenReturn(80L);

        scheduler.logStats();

        verify(statsCache, times(2)).calculateRatio();
    }

    // ── syncDriveVideos() ─────────────────────────────────────────────────────

    @Test
    void syncDriveVideos_happyPath_callsDownloadFileFromFolder() throws Exception {
        doNothing().when(videoStorageService).downloadFileFromFolder(anyString(), anyString());

        scheduler.syncDriveVideos();

        verify(videoStorageService).downloadFileFromFolder(anyString(), anyString());
    }

    @Test
    void syncDriveVideos_whenServiceThrows_exceptionIsCaughtAndNotRethrown() throws Exception {
        doThrow(new RuntimeException("drive error"))
                .when(videoStorageService).downloadFileFromFolder(anyString(), anyString());

        // Must not throw
        scheduler.syncDriveVideos();

        verify(videoStorageService).downloadFileFromFolder(anyString(), anyString());
    }

    // ── cleanUpOldVideos() ────────────────────────────────────────────────────

    @Test
    void cleanUpOldVideos_callsGetLeastAccessedVideos() {
        when(videoAccessService.getLeastAccessedVideos(any(Duration.class), anyInt()))
                .thenReturn(List.of());

        scheduler.cleanUpOldVideos();

        verify(videoAccessService).getLeastAccessedVideos(any(Duration.class), anyInt());
    }

    @Test
    void cleanUpOldVideos_withCandidates_iteratesWithoutDeletingYet() {
        when(videoAccessService.getLeastAccessedVideos(any(Duration.class), anyInt()))
                .thenReturn(List.of("video-1", "video-2", "video-3"));

        scheduler.cleanUpOldVideos();

        verify(videoAccessService).getLeastAccessedVideos(any(Duration.class), anyInt());
        // Storage service must not be touched (deletion is commented out in source)
        verifyNoInteractions(videoStorageService);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static VideoSource source(String sourceId) {
        VideoSource s = new VideoSource();
        s.setSourceId(sourceId);
        return s;
    }

    private static File createTempVideoFile(String sourceId) throws Exception {
        File f = new File("video-cache", sourceId + ".full");
        f.getParentFile().mkdirs();
        f.createNewFile();
        return f;
    }
}
