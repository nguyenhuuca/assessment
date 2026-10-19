package com.canhlabs.funnyapp.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads the seed migration from the classpath so it cannot drift from the code. */
class ScheduledJobMigrationTest {

    private static final Pattern ROW = Pattern.compile(
            "\\('([a-z-]+)',\\s*'([^']+)',\\s*(NULL|'[^']+'),\\s*(TRUE|FALSE),");

    private record Seed(String cron, String zone, boolean enabled) {
    }

    private static Map<String, Seed> seed() throws Exception {
        String sql = new ClassPathResource("db/changelog/sql/202610190002-create-scheduled-job.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Seed> rows = new LinkedHashMap<>();
        Matcher m = ROW.matcher(sql);
        while (m.find()) {
            String zone = "NULL".equals(m.group(3)) ? null : m.group(3).replace("'", "");
            rows.put(m.group(1), new Seed(m.group(2), zone, "TRUE".equals(m.group(4))));
        }
        return rows;
    }

    @Test
    void seed_holdsTheSixJobsWithTodaysSchedules() throws Exception {
        Map<String, Seed> rows = seed();

        assertThat(rows).containsOnlyKeys(
                JobRegistry.MAKE_POEM, JobRegistry.LOG_STATS, JobRegistry.SYNC_DRIVE_VIDEOS,
                JobRegistry.NOTIFICATION_DIGEST, JobRegistry.NOTIFICATION_RETENTION, JobRegistry.CLEANUP_OLD_VIDEOS);
        assertThat(rows.get(JobRegistry.MAKE_POEM)).isEqualTo(new Seed("0 45 10 * * *", "Asia/Ho_Chi_Minh", true));
        assertThat(rows.get(JobRegistry.LOG_STATS)).isEqualTo(new Seed("0 */5 * * * *", null, true));
        assertThat(rows.get(JobRegistry.SYNC_DRIVE_VIDEOS)).isEqualTo(new Seed("0 */15 * * * *", null, true));
        assertThat(rows.get(JobRegistry.NOTIFICATION_DIGEST)).isEqualTo(new Seed("0 */15 * * * *", null, true));
        assertThat(rows.get(JobRegistry.NOTIFICATION_RETENTION))
                .isEqualTo(new Seed("0 30 3 * * *", "Asia/Ho_Chi_Minh", true));
        assertThat(rows.get(JobRegistry.CLEANUP_OLD_VIDEOS).enabled()).isFalse();
    }

    @Test
    void seed_everyCronIsValid() throws Exception {
        seed().forEach((name, row) ->
                assertThat(CronExpression.isValidExpression(row.cron())).as(name).isTrue());
    }

    @Test
    void seed_everyNameExistsInRegistry() throws Exception {
        JobRegistry registry = new JobRegistry(org.mockito.Mockito.mock(AppScheduler.class));

        assertThat(registry.names()).containsExactlyInAnyOrderElementsOf(seed().keySet());
        seed().keySet().forEach(name -> assertThat(registry.get(name)).as(name).isNotNull());
        assertThat(registry.get("nope")).isNull();
    }
}
