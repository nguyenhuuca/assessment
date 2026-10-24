package com.canhlabs.funnyapp.repo;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the query text; the project has no database test harness. */
class WatchHistoryRepositoryQueryTest {

    @Test
    void upsertWatched_returnsWhetherTheRowWasInserted() throws Exception {
        Method m = WatchHistoryRepository.class.getMethod("upsertWatched", Long.class, Long.class);
        Query q = m.getAnnotation(Query.class);

        assertThat(q.nativeQuery()).isTrue();
        assertThat(q.value()).contains("ON CONFLICT (user_id, video_id) DO UPDATE").contains("RETURNING (xmax = 0)");
        assertThat(m.isAnnotationPresent(Modifying.class)).as("RETURNING needs a read, not an update count").isFalse();
        assertThat(m.getReturnType()).isEqualTo(Optional.class);
    }
}
