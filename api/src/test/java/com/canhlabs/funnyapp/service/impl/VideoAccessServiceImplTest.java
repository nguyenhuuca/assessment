package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoAccessStats;
import com.canhlabs.funnyapp.repo.VideoAccessStatsRepo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoAccessServiceImplTest {
    @Test
    void getLeastAccessedVideos_returnsVideoIds() {
        VideoAccessStatsRepo repo = Mockito.mock(VideoAccessStatsRepo.class);
        VideoAccessStats stat1 = Mockito.mock(VideoAccessStats.class);
        VideoAccessStats stat2 = Mockito.mock(VideoAccessStats.class);
        Mockito.when(stat1.getVideoId()).thenReturn("vid1");
        Mockito.when(stat2.getVideoId()).thenReturn("vid2");
        Mockito.when(repo.findLeastAccessed(Mockito.any(Instant.class), Mockito.any())).thenReturn(List.of(stat1, stat2));
        VideoAccessServiceImpl service = new VideoAccessServiceImpl(repo);
        List<String> result = service.getLeastAccessedVideos(Duration.ofDays(1), 2);
        assertThat(result).containsExactly("vid1", "vid2");
    }

    @Test
    void recordAccess_doesNotThrow() {
        VideoAccessStatsRepo repo = Mockito.mock(VideoAccessStatsRepo.class);
        VideoAccessServiceImpl service = new VideoAccessServiceImpl(repo);
        service.recordAccess("videoId"); // Should not throw, even if not implemented
    }

    @Test
    void getLeastAccessedVideos_emptyResult() {
        VideoAccessStatsRepo repo = Mockito.mock(VideoAccessStatsRepo.class);
        Mockito.when(repo.findLeastAccessed(Mockito.any(Instant.class), Mockito.any())).thenReturn(List.of());
        VideoAccessServiceImpl service = new VideoAccessServiceImpl(repo);
        List<String> result = service.getLeastAccessedVideos(Duration.ofDays(1), 2);
        assertThat(result).isEmpty();
    }

}
