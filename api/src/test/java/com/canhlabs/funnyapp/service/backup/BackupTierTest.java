package com.canhlabs.funnyapp.service.backup;

import com.canhlabs.funnyapp.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupTierTest {

    @Test
    void idsAreLowerCase() {
        assertThat(BackupTier.DAILY.id()).isEqualTo("daily");
        assertThat(BackupTier.WEEKLY.id()).isEqualTo("weekly");
        assertThat(BackupTier.MONTHLY.id()).isEqualTo("monthly");
    }

    @Test
    void retentionComesFromConfig() {
        AppProperties.Backup backup = new AppProperties.Backup();
        backup.getRetention().setDaily(1);
        backup.getRetention().setWeekly(2);
        backup.getRetention().setMonthly(3);

        assertThat(BackupTier.DAILY.retention(backup)).isEqualTo(1);
        assertThat(BackupTier.WEEKLY.retention(backup)).isEqualTo(2);
        assertThat(BackupTier.MONTHLY.retention(backup)).isEqualTo(3);
    }

    @Test
    void defaultRetention() {
        AppProperties.Backup backup = new AppProperties.Backup();

        assertThat(BackupTier.DAILY.retention(backup)).isEqualTo(7);
        assertThat(BackupTier.WEEKLY.retention(backup)).isEqualTo(4);
        assertThat(BackupTier.MONTHLY.retention(backup)).isEqualTo(12);
    }
}
