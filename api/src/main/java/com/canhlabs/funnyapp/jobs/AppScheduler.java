package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.dto.CacheStat;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import com.canhlabs.funnyapp.share.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AppScheduler {

    private final YouTubeVideoService service;
    private final StreamVideoService streamVideoService;
    private final StatsCache statsCache;
    private final VideoAccessService videoAccessService;
    private final VideoStorageService videoStorageService;


    public AppScheduler(YouTubeVideoService service, StreamVideoService streamVideoService,
                        StatsCache statsCache, VideoAccessService videoAccessService, VideoStorageService videoStorageService
    ) {
        this.service = service;
        this.streamVideoService = streamVideoService;
        this.statsCache = statsCache;
        this.videoAccessService = videoAccessService;
        this.videoStorageService = videoStorageService;
    }

    // Run at 1:00 daily
    // @Scheduled(cron = "0 15 10 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleProcessTop10() {
        log.info("Start running processTop10YouTube at 1AM");
        try {
            service.processTop10YouTube();
        } catch (Exception ex) {
            log.error("Error running processTop10YouTube job", ex);
        }
    }

    @Scheduled(cron = "0 45 10 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleMakePoem() {
        log.info("Start running scheduleMakePoem at 10:10 ");
        try {
            streamVideoService.updateDesc();
        } catch (Exception ex) {
            log.error("Error running scheduleMakePoem job", ex);
        }
    }

    @Scheduled(fixedRate = 5 * 60000)
    public void logStats() {
        log.info("📝 Cron-based stats log every 5 minutes for logStats");
        log.info("📊 Total hits: {}, Total misses: {}", statsCache.getTotalHits(), statsCache.getTotalMisses());
        for (Map.Entry<String, CacheStat> entry : statsCache.getFileStats().entrySet()) {
            String fileId = entry.getKey();
            CacheStat stat = entry.getValue();
            log.info("🎯 fileId: {}, hits: {}, misses: {}, hitRatio: {}%",
                    fileId, stat.getHits(), stat.getMisses(), statsCache.calculateRatio());
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // every 20 minutes
    public void syncDriveVideos() {
        log.info("📥 Syncing Google Drive folder...");
        Instant fifteenMinutesAgo = Instant.now().minus(Duration.ofMinutes(60));
        // iso format: "2025-01-01T00:00:00Z"
        String isoTime = DateTimeFormatter.ISO_INSTANT.format(fifteenMinutesAgo);

        try {
            videoStorageService.downloadFileFromFolder(AppConstant.FOLDER_ID, isoTime);
            log.info("✅ Successfully synced from Google Drive");
        } catch (Exception e) {
            log.error("❌ Failed to sync from Google Drive", e);
        }
    }


    @Scheduled(cron = "0 0 3 30 2 *")
    public void cleanUpOldVideos() {
        List<String> candidates = videoAccessService.getLeastAccessedVideos(Duration.ofDays(1000), 100);
        for (String videoId : candidates) {
            //storageService.deleteIfEligible(videoId);
        }
    }
}