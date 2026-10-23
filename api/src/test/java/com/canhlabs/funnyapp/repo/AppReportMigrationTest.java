package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the migration from the classpath so it cannot drift from the entity and enums.
 * The project has no database test harness (no Testcontainers/H2, and the DDL uses
 * Postgres-only features), so this checks the SQL text and the JPA mapping, not a live schema.
 */
class AppReportMigrationTest {

    private static String sql() throws Exception {
        return resource("db/changelog/sql/202610200001-create-app-report.sql");
    }

    /** ADR-0026 D7: webhook state columns added later. */
    private static String stateSql() throws Exception {
        return resource("db/changelog/sql/202610210001-app-report-github-state.sql");
    }

    /** Backend version stamped on each report, added later. */
    private static String apiVersionSql() throws Exception {
        return resource("db/changelog/sql/202610230001-app-report-api-version.sql");
    }

    private static String resource(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> checkValues(String sql, String column) {
        Matcher m = Pattern.compile(column + "\\s+VARCHAR\\(\\d+\\)\\s+(?:NOT NULL\\s+)?CHECK \\(" + column
                + " IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(m.find()).as("check constraint on " + column).isTrue();
        return Arrays.stream(m.group(1).split(",")).map(s -> s.trim().replace("'", "")).toList();
    }

    private static Field field(String name) throws Exception {
        return AppReport.class.getDeclaredField(name);
    }

    @Test
    void migration_createsTableAndBothIndexes() throws Exception {
        String sql = sql();

        assertThat(sql).contains("CREATE TABLE app_report");
        assertThat(sql).contains("CREATE INDEX idx_app_report_created_at ON app_report (created_at DESC)");
        assertThat(sql).containsPattern("CREATE INDEX idx_app_report_autopilot_cap ON app_report \\(created_at\\)\\s+"
                + "WHERE status = 'CREATED' AND autopilot AND reporter_role = 'ADMIN'");
        assertThat(sql).contains("reporter_id   BIGINT       REFERENCES users (id) ON DELETE SET NULL");
    }

    @Test
    void capIndexMigration_widensThePredicateToContributor() throws Exception {
        String sql = resource("db/changelog/sql/202610230002-app-report-cap-contributor.sql");

        assertThat(sql).contains("DROP INDEX IF EXISTS idx_app_report_autopilot_cap");
        assertThat(sql).containsPattern("CREATE INDEX idx_app_report_autopilot_cap ON app_report \\(created_at\\)\\s+"
                + "WHERE status = 'CREATED' AND autopilot AND reporter_role IN \\('ADMIN', 'CONTRIBUTOR'\\)");
    }

    @Test
    void checkConstraints_matchTheEnums() throws Exception {
        String sql = sql();

        assertThat(checkValues(sql, "type"))
                .containsExactlyElementsOf(Arrays.stream(ReportType.values()).map(Enum::name).toList());
        assertThat(checkValues(sql, "status"))
                .containsExactlyElementsOf(Arrays.stream(ReportStatus.values()).map(Enum::name).toList());
        assertThat(checkValues(sql, "auto_label"))
                .containsExactlyElementsOf(Arrays.stream(ReportAutoLabel.values()).map(Enum::name).toList());
    }

    @Test
    void entity_mapsEnumsAsStringAndEveryColumnExists() throws Exception {
        String sql = sql() + stateSql() + apiVersionSql();
        for (String enumField : List.of("reporterRole", "type", "autoLabel", "status", "githubState")) {
            Enumerated e = field(enumField).getAnnotation(Enumerated.class);
            assertThat(e).as(enumField).isNotNull();
            assertThat(e.value()).as(enumField).isEqualTo(EnumType.STRING);
        }
        assertThat(field("reporterRole").getType()).isEqualTo(UserRole.class);

        for (Field f : AppReport.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(sql).as(c.name()).containsPattern("(\\n\\s+|ADD COLUMN )" + c.name() + "\\s+");
            }
        }
    }

    @Test
    void entity_reporterIsLazyAndNullable() throws Exception {
        ManyToOne rel = field("reporter").getAnnotation(ManyToOne.class);

        assertThat(rel.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(rel.optional()).isTrue();
    }

    @Test
    void entity_defaultsLabelsToEmpty() {
        assertThat(AppReport.builder().build().getLabels()).isEmpty();
    }

    @Test
    void repository_pagedListIsNewestFirstWithReporterGraph() throws Exception {
        Method m = AppReportRepository.class.getMethod("findAllByOrderByCreatedAtDesc", Pageable.class);

        assertThat(m.getReturnType()).isEqualTo(Page.class);
        assertThat(m.getAnnotation(EntityGraph.class).attributePaths()).containsExactly("reporter");
    }

    @Test
    void repository_capCountFiltersCreatedAutopilotAdminAndContributorInWindow() throws Exception {
        Method m = AppReportRepository.class.getMethod(
                "countByStatusAndAutopilotTrueAndReporterRoleInAndCreatedAtAfter",
                ReportStatus.class, Collection.class, Instant.class);

        assertThat(m.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void repository_derivedQueryNamesResolveAgainstTheEntity() {
        new PartTree("findAllByOrderByCreatedAtDesc", AppReport.class);
        PartTree count = new PartTree("countByStatusAndAutopilotTrueAndReporterRoleInAndCreatedAtAfter", AppReport.class);

        assertThat(count.isCountProjection()).isTrue();
        assertThat(count.getParts()).hasSize(4);
    }

    @Test
    void stateMigration_addsNullableColumnsAndIssueIndex() throws Exception {
        String sql = stateSql();

        assertThat(sql).contains("ALTER TABLE app_report");
        assertThat(sql).doesNotContainPattern("ADD COLUMN [^\\n]*NOT NULL").doesNotContainIgnoringCase("DROP");
        assertThat(sql).contains("ADD COLUMN github_state_reason VARCHAR(16)");
        assertThat(sql).contains("ADD COLUMN github_state_at     TIMESTAMPTZ");
        assertThat(sql).contains(
                "CREATE INDEX idx_app_report_issue_number ON app_report (issue_number) WHERE issue_number IS NOT NULL");
        assertThat(checkValues(sql, "github_state"))
                .containsExactlyElementsOf(Arrays.stream(GitHubIssueState.values()).map(Enum::name).toList());
    }

    @Test
    void repository_applyGithubStateIsATransactionalModifyingUpdate() throws Exception {
        Method m = AppReportRepository.class.getMethod(
                "applyGithubState", int.class, GitHubIssueState.class, String.class, Instant.class);

        assertThat(m.getReturnType()).isEqualTo(int.class);
        assertThat(m.getAnnotation(Modifying.class)).isNotNull();
        assertThat(m.getAnnotation(Transactional.class)).isNotNull();
        assertThat(m.getAnnotation(Query.class).value())
                .contains("ReportStatus.CREATED")
                .contains("r.githubStateAt IS NULL OR r.githubStateAt < :at");
    }

    @Test
    void repository_existsByStatusAndIssueNumberResolves() {
        PartTree exists = new PartTree("existsByStatusAndIssueNumber", AppReport.class);

        assertThat(exists.isExistsProjection()).isTrue();
        assertThat(exists.getParts()).hasSize(2);
    }

    @Test
    void repository_closedNotificationLookupLoadsReporter() throws Exception {
        Method m = AppReportRepository.class.getMethod(
                "findFirstWithReporterByStatusAndIssueNumber", ReportStatus.class, Integer.class);
        PartTree tree = new PartTree("findFirstWithReporterByStatusAndIssueNumber", AppReport.class);

        assertThat(m.getAnnotation(EntityGraph.class).attributePaths()).containsExactly("reporter");
        assertThat(tree.isLimiting()).isTrue();
        assertThat(tree.getParts()).hasSize(2);
    }
}
