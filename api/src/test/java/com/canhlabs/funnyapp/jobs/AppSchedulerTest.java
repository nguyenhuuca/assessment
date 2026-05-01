package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
