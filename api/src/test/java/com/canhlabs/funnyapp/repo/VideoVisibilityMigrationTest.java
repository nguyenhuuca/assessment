package com.canhlabs.funnyapp.repo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the SQL text; the project has no database test harness. */
class VideoVisibilityMigrationTest {

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new ClassPathResource("db/changelog/sql/202610300001-video-visibility.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    @Test
    void migration_isFormattedSqlWithOneChangeset() {
        assertThat(sql).startsWith("--liquibase formatted sql");
        assertThat(sql).contains("--changeset nguyenhuuca:202610300001-video-visibility");
        assertThat(sql.split("--changeset", -1)).hasSize(2);
    }

    @Test
    void migration_addsVisibilityColumnBackfillsAndChecks() {
        assertThat(sql).contains("ALTER TABLE video_sources ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';");
        assertThat(sql).contains("UPDATE video_sources SET visibility = 'HIDDEN' WHERE is_hide = true;");
        assertThat(sql).contains("ADD CONSTRAINT chk_video_sources_visibility "
                + "CHECK (visibility IN ('FEED', 'COLLECTION_ONLY', 'HIDDEN'));");
        assertThat(sql).contains("ALTER TABLE video_import_jobs ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';");
        assertThat(sql).contains("ADD CONSTRAINT chk_video_import_jobs_visibility "
                + "CHECK (visibility IN ('FEED', 'COLLECTION_ONLY'));");
    }

    @Test
    void migration_recreatesFeedIndexOnVisibilityBeforeDroppingIsHide() {
        String recreate = "DROP INDEX IF EXISTS idx_video_sources_feed; "
                + "CREATE INDEX idx_video_sources_feed ON video_sources (priority DESC, created_at DESC) "
                + "WHERE visibility = 'FEED' AND status = 'PUBLISHED';";
        String drop = "ALTER TABLE video_sources DROP COLUMN is_hide;";
        assertThat(sql).contains(recreate).contains(drop);
        assertThat(sql.indexOf(recreate)).isLessThan(sql.indexOf(drop));
    }

    @Test
    void migration_addsDriveFileIdIndexOnImportJobs() {
        assertThat(sql).contains("CREATE INDEX idx_video_import_jobs_drive_file_id "
                + "ON video_import_jobs (drive_file_id) WHERE drive_file_id IS NOT NULL;");
    }

    @Test
    void migration_carriesFullRollback() {
        assertThat(sql).contains("--rollback DROP INDEX IF EXISTS idx_video_import_jobs_drive_file_id;");
        assertThat(sql).contains("--rollback ALTER TABLE video_import_jobs DROP CONSTRAINT chk_video_import_jobs_visibility;");
        assertThat(sql).contains("--rollback ALTER TABLE video_import_jobs DROP COLUMN visibility;");
        assertThat(sql).contains("--rollback ALTER TABLE video_sources ADD COLUMN is_hide BOOLEAN NOT NULL DEFAULT false;");
        assertThat(sql).contains("--rollback UPDATE video_sources SET is_hide = (visibility = 'HIDDEN');");
        assertThat(sql).contains("--rollback CREATE INDEX idx_video_sources_feed ON video_sources "
                + "(priority DESC, created_at DESC) WHERE is_hide = false AND status = 'PUBLISHED';");
        assertThat(sql).contains("--rollback ALTER TABLE video_sources DROP CONSTRAINT chk_video_sources_visibility;");
        assertThat(sql).contains("--rollback ALTER TABLE video_sources DROP COLUMN visibility;");
    }
}
