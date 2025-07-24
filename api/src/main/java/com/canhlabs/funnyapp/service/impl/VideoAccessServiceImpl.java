package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoAccessStats;
import com.canhlabs.funnyapp.repo.VideoAccessStatsRepo;
import com.canhlabs.funnyapp.service.VideoAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class VideoAccessServiceImpl implements VideoAccessService {
    private final VideoAccessStatsRepo repo;
    public VideoAccessServiceImpl(VideoAccessStatsRepo repo) {
        this.repo = repo;
    }

    @Async
    @Override
    public void recordAccess(String videoId) {
        // Todo: will be implemented later after we create new db cluster
//        VideoAccessStats stats = repo.findByVideoId(videoId)
//                .orElseGet(() -> VideoAccessStats.builder().videoId(videoId).build());
//        stats.incrementHit();
//        repo.save(stats);
    }

    @Override
    public List<String> getLeastAccessedVideos(Duration olderThan, int limit) {
        Instant before = Instant.now().minus(olderThan);
        List<VideoAccessStats> results = repo.findLeastAccessed(before, PageRequest.of(0, limit));
        return results.stream().map(VideoAccessStats::getVideoId).toList();
    }
}
