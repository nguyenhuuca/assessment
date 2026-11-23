package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesBackupTest {

    @Test
    void defaults() {
        AppProperties.Backup b = new AppProperties().getBackup();

        assertThat(b.isEnabled()).isFalse();
        assertThat(b.getFolderId()).isEmpty();
        assertThat(b.getPgDumpPath()).isEqualTo("pg_dump");
        assertThat(b.getWorkDir()).isEqualTo("/opt/data/backups");
        assertThat(b.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(b.getAlertEmail()).isEmpty();
        assertThat(b.getRetention().getDaily()).isEqualTo(7);
        assertThat(b.getRetention().getWeekly()).isEqualTo(4);
        assertThat(b.getRetention().getMonthly()).isEqualTo(12);
    }

    @Test
    void bindsFromProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.backup.enabled", "true",
                "app.backup.folder-id", "folder1",
                "app.backup.pg-dump-path", "/usr/bin/pg_dump",
                "app.backup.work-dir", "/tmp/b",
                "app.backup.timeout", "2m",
                "app.backup.alert-email", "ops@example.com",
                "app.backup.retention.daily", "3",
                "app.backup.retention.weekly", "2",
                "app.backup.retention.monthly", "6"));

        AppProperties.Backup b = new Binder(source).bind("app", AppProperties.class).get().getBackup();

        assertThat(b.isEnabled()).isTrue();
        assertThat(b.getFolderId()).isEqualTo("folder1");
        assertThat(b.getPgDumpPath()).isEqualTo("/usr/bin/pg_dump");
        assertThat(b.getWorkDir()).isEqualTo("/tmp/b");
        assertThat(b.getTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(b.getAlertEmail()).isEqualTo("ops@example.com");
        assertThat(b.getRetention().getDaily()).isEqualTo(3);
        assertThat(b.getRetention().getWeekly()).isEqualTo(2);
        assertThat(b.getRetention().getMonthly()).isEqualTo(6);
    }
}
