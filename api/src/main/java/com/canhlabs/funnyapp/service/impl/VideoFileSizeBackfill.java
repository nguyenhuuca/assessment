package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Startup backfill: records the cached file size of Drive videos saved before the file_size column existed.
 * Runs once per boot on a virtual thread; rows whose file is missing stay null.
 */
@Slf4j
@Component
public class VideoFileSizeBackfill {
    private static final String DRIVE_SOURCE = "google_drive";

    private final VideoSourceRepository videoSourceRepository;
    private final VideoStorageService videoStorageService;

    public VideoFileSizeBackfill(VideoSourceRepository videoSourceRepository,
                                 VideoStorageService videoStorageService) {
        this.videoSourceRepository = videoSourceRepository;
        this.videoStorageService = videoStorageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread.ofVirtual().name("video-file-size-backfill").start(this::backfill);
    }

    /** Synchronous body, exposed for tests. Returns the number of rows updated. */
    int backfill() {
        int updated = 0;
        try {
            List<VideoSource> rows = videoSourceRepository.findBySourceTypeAndFileSizeIsNull(DRIVE_SOURCE);
            for (VideoSource row : rows) {
                if (fillOne(row)) {
                    updated++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Video file size backfill aborted: {}", e.getMessage());
        }
        log.info("Video file size backfill updated {} rows", updated);
        return updated;
    }

    private boolean fillOne(VideoSource row) {
        try {
            row.setFileSize(videoStorageService.getFileSizeFromDisk(row.getSourceId()));
            videoSourceRepository.save(row);
            return true;
        } catch (IOException e) {
            return false; // file not cached: leave null
        } catch (RuntimeException e) {
            log.warn("Could not backfill file size for {}: {}", row.getSourceId(), e.getMessage());
            return false;
        }
    }
}
