package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoFileSizeBackfillTest {

    @Mock VideoSourceRepository videoSourceRepository;
    @Mock VideoStorageService videoStorageService;

    private VideoSource row(String id) {
        return VideoSource.builder().id((long) id.hashCode()).sourceId(id).sourceType("google_drive").build();
    }

    private VideoFileSizeBackfill backfill() {
        return new VideoFileSizeBackfill(videoSourceRepository, videoStorageService);
    }

    @Test
    void backfill_fillsRowsWithCachedFile() throws Exception {
        VideoSource a = row("a");
        when(videoSourceRepository.findBySourceTypeAndFileSizeIsNull("google_drive")).thenReturn(List.of(a));
        when(videoStorageService.getFileSizeFromDisk("a")).thenReturn(99L);

        when(videoSourceRepository.updateFileSizeIfNull(a.getId(), 99L)).thenReturn(1);

        assertThat(backfill().backfill()).isEqualTo(1);

        verify(videoSourceRepository).updateFileSizeIfNull(a.getId(), 99L);
        verify(videoSourceRepository, never()).save(any());
    }

    @Test
    void backfill_missingFile_skipsRow() throws Exception {
        VideoSource a = row("a");
        when(videoSourceRepository.findBySourceTypeAndFileSizeIsNull("google_drive")).thenReturn(List.of(a));
        when(videoStorageService.getFileSizeFromDisk("a")).thenThrow(new FileNotFoundException("gone"));

        assertThat(backfill().backfill()).isZero();

        verify(videoSourceRepository, never()).updateFileSizeIfNull(any(), anyLong());
    }

    @Test
    void backfill_failingRow_continuesWithNext() throws Exception {
        VideoSource bad = row("bad");
        VideoSource good = row("good");
        when(videoSourceRepository.findBySourceTypeAndFileSizeIsNull("google_drive")).thenReturn(List.of(bad, good));
        when(videoStorageService.getFileSizeFromDisk("bad")).thenReturn(1L);
        when(videoStorageService.getFileSizeFromDisk("good")).thenReturn(2L);
        when(videoSourceRepository.updateFileSizeIfNull(bad.getId(), 1L)).thenThrow(new IllegalStateException("db down"));
        when(videoSourceRepository.updateFileSizeIfNull(good.getId(), 2L)).thenReturn(1);

        assertThat(backfill().backfill()).isEqualTo(1);

        verify(videoSourceRepository).updateFileSizeIfNull(good.getId(), 2L);
    }

    @Test
    void backfill_finderFails_doesNotThrow() {
        when(videoSourceRepository.findBySourceTypeAndFileSizeIsNull("google_drive"))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(backfill().backfill()).isZero();
    }

    @Test
    void onApplicationReady_startsWithoutThrowing() {
        backfill().onApplicationReady();
    }
}
