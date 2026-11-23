package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.JobRun;
import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** No database test harness in this project, so this checks the SQL text against the JPA mapping. */
class JobRunMigrationTest {

    private static String sql() throws Exception {
        return new ClassPathResource("db/changelog/sql/202611180001-job-run.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void everyMappedColumnExistsInTheMigration() throws Exception {
        String sql = sql();
        assertThat(JobRun.class.getAnnotation(Table.class).name()).isEqualTo("job_run");
        assertThat(sql).contains("CREATE TABLE job_run");
        for (Field f : JobRun.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(sql).as(c.name()).containsPattern("(?m)^\\s+" + c.name() + "\\s");
            }
        }
    }

    @Test
    void historyIsRemovedWithItsJobAndIndexedNewestFirst() throws Exception {
        assertThat(sql())
                .contains("REFERENCES scheduled_job (name) ON DELETE CASCADE")
                .contains("ON job_run (job_name, started_at DESC, id DESC)");
    }

    @Test
    void enumColumnsFitEveryValue() throws Exception {
        assertThat(sql()).contains("run_trigger VARCHAR(16)").contains("result      VARCHAR(16)");
        assertThat(Stream.concat(Stream.of(JobRunResult.values()).map(Enum::name),
                Stream.of(JobTrigger.values()).map(Enum::name)))
                .allSatisfy(v -> assertThat(v.length()).isLessThanOrEqualTo(16));
    }
}
