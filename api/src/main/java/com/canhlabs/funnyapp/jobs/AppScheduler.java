package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.cache.CacheStat;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.canhlabs.funnyapp.service.notification.NotificationDigestService;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Job bodies only; schedules live in the scheduled_job table (see {@link DynamicJobScheduler}). */
@Slf4j
@Service
public class AppScheduler implements ApplicationRunner {

    private final StreamVideoService streamVideoService;
    private final StatsCache statsCache;
    private final VideoAccessService videoAccessService;
    private final VideoStorageService videoStorageService;
    private final FfmpegService ffmpegService;
    private final VideoSourceRepository videoSourceRepository;
    private final AppProperties appProps;
    private final NotificationDigestService notificationDigestService;

    @Value("${app.jobs.regenerate-thumbnails:false}")
    private boolean regenerateThumbnailsEnabled;

    public AppScheduler(YouTubeVideoService service, StreamVideoService streamVideoService,
                        StatsCache statsCache, VideoAccessService videoAccessService,
                        VideoStorageService videoStorageService, FfmpegService ffmpegService,
                        VideoSourceRepository videoSourceRepository, AppProperties appProps,
                        NotificationDigestService notificationDigestService
    ) {
        this.streamVideoService = streamVideoService;
        this.statsCache = statsCache;
        this.videoAccessService = videoAccessService;
        this.videoStorageService = videoStorageService;
        this.ffmpegService = ffmpegService;
        this.videoSourceRepository = videoSourceRepository;
        this.appProps = appProps;
        this.notificationDigestService = notificationDigestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (regenerateThumbnailsEnabled) {
            regenerateThumbnails();
        }
    }

    public void scheduleMakePoem() {
        log.info("Start running scheduleMakePoem");
        streamVideoService.updateDesc();
    }

    public void logStats() {
        log.info("Cron-based stats log every 5 minutes for logStats");
        log.info("Total hits: {}, Total misses: {}", statsCache.getTotalHits(), statsCache.getTotalMisses());
        for (Map.Entry<String, CacheStat> entry : statsCache.getFileStats().entrySet()) {
            String fileId = entry.getKey();
            CacheStat stat = entry.getValue();
            log.info("fileId: {}, hits: {}, misses: {}, hitRatio: {}%",
                    fileId, stat.getHits(), stat.getMisses(), statsCache.calculateRatio());
        }
    }

    public void syncDriveVideos() {
        log.info("Syncing Google Drive folder...");
        Instant fifteenMinutesAgo = Instant.now().minus(Duration.ofMinutes(60));
        // iso format: "2025-01-01T00:00:00Z"
        String isoTime = DateTimeFormatter.ISO_INSTANT.format(fifteenMinutesAgo);

        try {
            videoStorageService.downloadFileFromFolder(AppConstant.FOLDER_ID, isoTime);
            log.info("✅ Successfully synced from Google Drive");
        } catch (IOException e) {
            throw new UncheckedIOException("Google Drive sync failed", e);
        }
    }


    public void notificationDigest() {
        int sent = notificationDigestService.sendDigests();
        log.info("Notification digest sent {} email(s)", sent);
    }

    public void notificationRetention() {
        int deleted = notificationDigestService.purgeOldRead(Instant.now());
        log.info("Notification retention deleted {} read notification(s)", deleted);
    }

    public void cleanUpOldVideos() {
        List<String> candidates = videoAccessService.getLeastAccessedVideos(Duration.ofDays(1000), 100);
        for (String videoId : candidates) {
            //storageService.deleteIfEligible(videoId);
        }
    }

    public void regenerateThumbnails() {
        log.info("▶ Starting one-time thumbnail regeneration job...");
        List<VideoSource> sources = videoSourceRepository.findAllByOrderByCreatedAtDesc();
        int processed = 0, skipped = 0, failed = 0;

        for (VideoSource source : sources) {
            String sourceId = source.getSourceId();
            File localFile = new File(AppConstant.CACHE_DIR, sourceId + ".full");

            if (!localFile.exists()) {
                log.debug("Local file missing for sourceId={}, skipping", sourceId);
                skipped++;
                continue;
            }

            try {
                String imageName = sourceId + ".jpg";
                String thumbnailPath = Paths.get(
                        appProps.getImageStoragePath().concat("/thumbnails"), imageName).toString();
                ffmpegService.generateThumbnail(localFile.getAbsolutePath(), thumbnailPath);
                source.setThumbnailPath(appProps.getImageUrl() + "/" + imageName);
                videoSourceRepository.save(source);
                processed++;
                log.info("✅ Thumbnail generated for sourceId={}", sourceId);
            } catch (Exception e) {
                log.error("❌ Failed to generate thumbnail for sourceId={}: {}", sourceId, e.getMessage());
                failed++;
            }
        }

        log.info("✔ Thumbnail regeneration done — processed={}, skipped={}, failed={}", processed, skipped, failed);
    }
}