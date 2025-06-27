package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.service.StorageVideoService;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AppScheduler {

    private final YouTubeVideoService service;
    private final StorageVideoService googleDriveVideoService;

    public AppScheduler(YouTubeVideoService service, StorageVideoService googleDriveVideoService) {
        this.service = service;
        this.googleDriveVideoService = googleDriveVideoService;
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

    @Scheduled(cron = "0 10 10 * * *", zone = "Asia/Ho_Chi_Minh")
    public void scheduleMakePoem() {
        log.info("Start running scheduleMakePoem at 10:10 ");
        try {
            googleDriveVideoService.updateDesc();
        } catch (Exception ex) {
            log.error("Error running scheduleMakePoem job", ex);
        }
    }
}