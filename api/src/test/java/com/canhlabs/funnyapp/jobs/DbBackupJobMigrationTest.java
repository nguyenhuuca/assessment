package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.service.DbBackupService;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Reads the backup seed migration from the classpath so it cannot drift from the code (ADR-0039). */
class DbBackupJobMigrationTest {

    private static final Pattern ROW = Pattern.compile(
            "\\('([a-z-]+)',\\s*'([^']+)',\\s*'([^']+)',\\s*(TRUE|FALSE),");

    private record Seed(String cron, String zone, boolean enabled) {
    }

    private static Map<String, Seed> seed() throws Exception {
        String sql = new ClassPathResource("db/changelog/sql/202611170001-db-backup-jobs.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Seed> rows = new LinkedHashMap<>();
        Matcher m = ROW.matcher(sql);
        while (m.find()) {
            rows.put(m.group(1), new Seed(m.group(2), m.group(3), "TRUE".equals(m.group(4))));
        }
        return rows;
    }

    @Test
    void seed_holdsTheThreeTiersWithSpecSchedules() throws Exception {
        Map<String, Seed> rows = seed();

        assertThat(rows).containsOnlyKeys(
                JobRegistry.DB_BACKUP_DAILY, JobRegistry.DB_BACKUP_WEEKLY, JobRegistry.DB_BACKUP_MONTHLY);
        assertThat(rows.get(JobRegistry.DB_BACKUP_DAILY)).isEqualTo(new Seed("0 0 3 * * *", "Asia/Ho_Chi_Minh", true));
        assertThat(rows.get(JobRegistry.DB_BACKUP_WEEKLY)).isEqualTo(new Seed("0 30 3 * * SUN", "Asia/Ho_Chi_Minh", true));
        assertThat(rows.get(JobRegistry.DB_BACKUP_MONTHLY)).isEqualTo(new Seed("0 0 4 1 * *", "Asia/Ho_Chi_Minh", true));
    }

    @Test
    void seed_everyCronIsValid() throws Exception {
        seed().forEach((name, row) -> assertThat(CronExpression.isValidExpression(row.cron())).as(name).isTrue());
    }

    @Test
    void registry_runsEachTierThroughTheBackupService() throws Exception {
        DbBackupService backup = mock(DbBackupService.class);
        AppScheduler scheduler = new AppScheduler(null, null, null, null, null, null, null, null, null, null, null, backup);
        JobRegistry registry = new JobRegistry(scheduler);

        registry.get(JobRegistry.DB_BACKUP_DAILY).run();
        registry.get(JobRegistry.DB_BACKUP_WEEKLY).run();
        registry.get(JobRegistry.DB_BACKUP_MONTHLY).run();

        verify(backup).run(BackupTier.DAILY);
        verify(backup).run(BackupTier.WEEKLY);
        verify(backup).run(BackupTier.MONTHLY);
        seed().keySet().forEach(name -> assertThat(registry.get(name)).as(name).isNotNull());
    }
}
