package com.canhlabs.funnyapp.repo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the SQL text; the project has no database test harness. */
class VideoSourceFeedIndexMigrationTest {

    @Test
    void migration_createsPartialFeedIndex() throws Exception {
        String sql = new ClassPathResource("db/changelog/sql/202610240002-video-sources-feed-index.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_video_sources_feed "
                + "ON video_sources (priority DESC, created_at DESC) "
                + "WHERE is_hide = false AND status = 'PUBLISHED';");
        assertThat(sql).doesNotContainIgnoringCase("DROP");
    }
}
