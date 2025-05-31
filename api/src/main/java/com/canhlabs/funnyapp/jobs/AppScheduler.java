package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.service.impl.YouTubeVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AppScheduler {

    private final YouTubeVideoService service;

    public AppScheduler(YouTubeVideoService service) {
        this.service = service;
    }

    // Run at 1:00 daily
    @Scheduled(cron = "0 0 1 * * *",  zone = "UTC")
    public void scheduleProcessTop10() {
        log.info("Start running processTop10YouTube at 1AM");

        try {
            service.processTop10YouTube();
        } catch (Exception ex) {
            log.error("Error running processTop10YouTube job", ex);
        }
    }
}