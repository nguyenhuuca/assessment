package com.canhlabs.funnyapp.jobs;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Maps the job names stored in {@code scheduled_job} to the code in {@link AppScheduler}. */
@Component
public class JobRegistry {
    public static final String MAKE_POEM = "make-poem";
    public static final String LOG_STATS = "log-stats";
    public static final String SYNC_DRIVE_VIDEOS = "sync-drive-videos";
    public static final String NOTIFICATION_DIGEST = "notification-digest";
    public static final String NOTIFICATION_RETENTION = "notification-retention";
    public static final String CLEANUP_OLD_VIDEOS = "cleanup-old-videos";

    private final Map<String, Runnable> jobs;

    public JobRegistry(AppScheduler scheduler) {
        this.jobs = Map.of(
                MAKE_POEM, scheduler::scheduleMakePoem,
                LOG_STATS, scheduler::logStats,
                SYNC_DRIVE_VIDEOS, scheduler::syncDriveVideos,
                NOTIFICATION_DIGEST, scheduler::notificationDigest,
                NOTIFICATION_RETENTION, scheduler::notificationRetention,
                CLEANUP_OLD_VIDEOS, scheduler::cleanUpOldVideos);
    }

    /** Null when no code is registered under this name. */
    public Runnable get(String name) {
        return jobs.get(name);
    }

    public Set<String> names() {
        return jobs.keySet();
    }
}
