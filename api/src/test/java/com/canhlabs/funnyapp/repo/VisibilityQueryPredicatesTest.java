package com.canhlabs.funnyapp.repo;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the visibility predicates (spec Rules 3 and 7) by reading the query text:
 * the project has no database test harness.
 */
class VisibilityQueryPredicatesTest {

    private static final String PLAYABLE = "visibility <> 'HIDDEN'";

    private static String query(Class<?> repo, String method) {
        return Arrays.stream(repo.getMethods())
                .filter(m -> m.getName().equals(method))
                .map(m -> m.getAnnotation(Query.class))
                .filter(q -> q != null)
                .map(q -> q.value().replaceAll("\\s+", " "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no @Query on " + repo.getSimpleName() + "." + method));
    }

    private static List<String> queriesJoiningVideoSources(Class<?> repo) {
        return Arrays.stream(repo.getDeclaredMethods())
                .map(m -> m.getAnnotation(Query.class))
                .filter(q -> q != null)
                .flatMap(q -> java.util.stream.Stream.of(q.value(), q.countQuery()))
                .map(sql -> sql.replaceAll("\\s+", " "))
                .filter(sql -> sql.contains("video_sources"))
                .toList();
    }

    @Test
    void collectionItemVisibleConstant_isThePlayableRule() {
        assertThat(VideoCollectionItemRepository.VISIBLE).isEqualTo("v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'");
    }

    @Test
    void collectionItemQueries_useTheVisibleConstant() throws Exception {
        for (Method m : VideoCollectionItemRepository.class.getDeclaredMethods()) {
            Query q = m.getAnnotation(Query.class);
            if (q == null || !q.value().contains("video_sources")) {
                continue;
            }
            String sql = q.value().replaceAll("\\s+", " ");
            assertThat(sql).as(m.getName()).contains(VideoCollectionItemRepository.VISIBLE);
            assertThat(sql).as(m.getName()).doesNotContain("is_hide");
        }
    }

    @Test
    void watchHistoryQueriesOnVideoSources_acceptCollectionOnlyAndRejectHidden() {
        List<String> sqls = queriesJoiningVideoSources(WatchHistoryRepository.class);

        assertThat(sqls).hasSizeGreaterThanOrEqualTo(3);
        assertThat(sqls).allSatisfy(sql -> {
            assertThat(sql).contains(PLAYABLE).contains("status = 'PUBLISHED'");
            assertThat(sql).doesNotContain("is_hide").doesNotContain("visibility = 'FEED'");
        });
    }

    @Test
    void incrementViewCount_countsEveryNonHiddenVideo() {
        String sql = query(VideoSourceRepository.class, "incrementViewCount");

        assertThat(sql).contains("AND " + PLAYABLE).doesNotContain("is_hide").doesNotContain("visibility = 'FEED'");
    }

    @Test
    void upsertDriveSource_writesVisibilityOnInsertAndNeverOnConflict() {
        String sql = query(VideoSourceRepository.class, "upsertDriveSource");
        String insert = sql.substring(0, sql.indexOf("ON CONFLICT"));
        String onConflict = sql.substring(sql.indexOf("ON CONFLICT"));

        assertThat(insert).contains("visibility").contains(":visibility");
        assertThat(onConflict).doesNotContain("visibility");
        assertThat(Pattern.compile("is_hide").matcher(sql).find()).isFalse();
    }
}
