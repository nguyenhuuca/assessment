package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.dto.CacheStat;
import com.canhlabs.funnyapp.service.CacheStatsService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AppScheduler {

    private final YouTubeVideoService service;
    private final StreamVideoService googleDriveVideoService;
    private final CacheStatsService cacheStatsService;


    public AppScheduler(YouTubeVideoService service, StreamVideoService googleDriveVideoService, CacheStatsService cacheStatsService) {
        this.service = service;
        this.googleDriveVideoService = googleDriveVideoService;
        this.cacheStatsService = cacheStatsService;
    }

    // Run at 1:00 daily
    @Scheduled(cron = "0 15 10 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleProcessTop10() {
        log.info("Start running processTop10YouTube at 1AM");
        try {
            service.processTop10YouTube();
        } catch (Exception ex) {
            log.error("Error running processTop10YouTube job", ex);
        }
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleProcessShareFile() {
        log.info("Start running scheduleProcessShareFile each 15 minutes");
        try {
            googleDriveVideoService.shareFilesInFolder();
        } catch (Exception ex) {
            log.error("Error running scheduleProcessShareFile job", ex);
        }
    }

    @Scheduled(cron = "0 45 10 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleMakePoem() {
        log.info("Start running scheduleMakePoem at 10:10 ");
        try {
            googleDriveVideoService.updateDesc();
        } catch (Exception ex) {
            log.error("Error running scheduleMakePoem job", ex);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logStats() {
        log.info("📝 Cron-based stats log every 5 minutes for logStats");
        log.info("📊 Total hits: {}, Total misses: {}", cacheStatsService.getTotalHits(), cacheStatsService.getTotalMisses());
        for (Map.Entry<String, CacheStat> entry : cacheStatsService.getFileStats().entrySet()) {
            String fileId = entry.getKey();
            CacheStat stat = entry.getValue();
            log.info("🎯 fileId: {}, hits: {}, misses: {}, hitRatio: {}%",
                    fileId, stat.getHits(), stat.getMisses(), cacheStatsService.calculateRatio());
        }
    }
}