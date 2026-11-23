package com.canhlabs.funnyapp.service.backup;

import com.canhlabs.funnyapp.config.AppProperties;

import java.util.function.ToIntFunction;

/** Backup schedule tier with its retention count. */
public enum BackupTier {
    DAILY("daily", AppProperties.Backup.Retention::getDaily),
    WEEKLY("weekly", AppProperties.Backup.Retention::getWeekly),
    MONTHLY("monthly", AppProperties.Backup.Retention::getMonthly);

    private final String id;
    private final ToIntFunction<AppProperties.Backup.Retention> retention;

    BackupTier(String id, ToIntFunction<AppProperties.Backup.Retention> retention) {
        this.id = id;
        this.retention = retention;
    }

    public String id() {
        return id;
    }

    public int retention(AppProperties.Backup backup) {
        return retention.applyAsInt(backup.getRetention());
    }
}
