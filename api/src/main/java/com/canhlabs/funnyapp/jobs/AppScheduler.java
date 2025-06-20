package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.service.YouTubeVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AppScheduler {

    private final com.canhlabs.funnyapp.service.YouTubeVideoService service;

    public AppScheduler(YouTubeVideoService service) {
        this.service = service;
    }

    // Run at 1:00 daily
    @Scheduled(cron = "0 15 10 * * *",  zone = "Asia/Ho_Chi_Minh")
    public void scheduleProcessTop10() {
        log.info("Start running processTop10YouTube at 1AM");
        try {
            service.processTop10YouTube();
        } catch (Exception ex) {
            log.error("Error running processTop10YouTube job", ex);
        }
    }
}