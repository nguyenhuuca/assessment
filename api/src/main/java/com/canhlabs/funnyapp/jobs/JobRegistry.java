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
    public static final String EXPIRE_PAYMENT_ORDERS = "expire-payment-orders";
    public static final String RECONCILE_PAYMENTS = "reconcile-payments";
    public static final String DB_BACKUP_DAILY = "db-backup-daily";
    public static final String DB_BACKUP_WEEKLY = "db-backup-weekly";
    public static final String DB_BACKUP_MONTHLY = "db-backup-monthly";

    private final Map<String, Runnable> jobs;

    public JobRegistry(AppScheduler scheduler) {
        this.jobs = Map.ofEntries(
                Map.entry(MAKE_POEM, scheduler::scheduleMakePoem),
                Map.entry(LOG_STATS, scheduler::logStats),
                Map.entry(SYNC_DRIVE_VIDEOS, scheduler::syncDriveVideos),
                Map.entry(NOTIFICATION_DIGEST, scheduler::notificationDigest),
                Map.entry(NOTIFICATION_RETENTION, scheduler::notificationRetention),
                Map.entry(CLEANUP_OLD_VIDEOS, scheduler::cleanUpOldVideos),
                Map.entry(EXPIRE_PAYMENT_ORDERS, scheduler::expirePaymentOrders),
                Map.entry(RECONCILE_PAYMENTS, scheduler::reconcilePayments),
                Map.entry(DB_BACKUP_DAILY, scheduler::dbBackupDaily),
                Map.entry(DB_BACKUP_WEEKLY, scheduler::dbBackupWeekly),
                Map.entry(DB_BACKUP_MONTHLY, scheduler::dbBackupMonthly));
    }

    /** Null when no code is registered under this name. */
    public Runnable get(String name) {
        return jobs.get(name);
    }

    public Set<String> names() {
        return jobs.keySet();
    }
}
