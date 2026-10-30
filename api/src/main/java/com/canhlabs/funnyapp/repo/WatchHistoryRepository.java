package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.WatchHistory;
import com.canhlabs.funnyapp.entity.WatchHistoryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, WatchHistoryId> {

    /** One history row joined with its public video. */
    interface HistoryRow {
        Long getVideoId();

        String getTitle();

        String getFileId();

        long getViewCount();

        Instant getWatchedAt();

        int getPositionSec();

        Integer getDurationSec();
    }

    /** Lightweight row for the badge lookup. */
    interface SummaryRow {
        Long getVideoId();

        int getPositionSec();

        Integer getDurationSec();
    }

    /**
     * Inserts the entry or moves it to the top. Returns {@code true} when the row was inserted and {@code false}
     * when an existing one was moved (PostgreSQL {@code xmax = 0} only on a fresh insert). Empty when the video is
     * unknown or not public, in which case nothing is written. Not {@code @Modifying}: it reads the RETURNING row.
     */
    @Query(value = """
            INSERT INTO watch_history (user_id, video_id, watched_at)
            SELECT :userId, v.id, now() FROM video_sources v
             WHERE v.id = :videoId AND v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'
            ON CONFLICT (user_id, video_id) DO UPDATE SET watched_at = now()
            RETURNING (xmax = 0)
            """, nativeQuery = true)
    Optional<Boolean> upsertWatched(@Param("userId") Long userId, @Param("videoId") Long videoId);

    /** Evicts everything older than the {@code cap} newest entries of the user. */
    @Modifying
    @Query(value = """
            DELETE FROM watch_history
             WHERE user_id = :userId AND video_id IN (
                   SELECT video_id FROM watch_history WHERE user_id = :userId
                    ORDER BY watched_at DESC, video_id DESC OFFSET :cap)
            """, nativeQuery = true)
    int trimToCap(@Param("userId") Long userId, @Param("cap") int cap);

    /** Update-only: never creates a row. */
    @Modifying
    @Query(value = """
            UPDATE watch_history SET position_sec = :pos, duration_sec = :dur
             WHERE user_id = :userId AND video_id = :videoId
            """, nativeQuery = true)
    int updateProgress(@Param("userId") Long userId, @Param("videoId") Long videoId,
                       @Param("pos") int pos, @Param("dur") int dur);

    /**
     * Newest first, public videos only. {@code pattern} is a ready LIKE pattern whose wildcards are
     * escaped with a backslash, or null for no filter.
     */
    @Query(value = """
            SELECT h.video_id AS "videoId", v.title AS "title", v.source_id AS "fileId",
                   v.view_count AS "viewCount", h.watched_at AS "watchedAt",
                   h.position_sec AS "positionSec", h.duration_sec AS "durationSec"
              FROM watch_history h JOIN video_sources v ON v.id = h.video_id
             WHERE h.user_id = :userId AND v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'
               AND (CAST(:pattern AS text) IS NULL
                    OR lower(v.title) LIKE lower(CAST(:pattern AS text)) ESCAPE '\\')
             ORDER BY h.watched_at DESC, h.video_id DESC
            """,
            countQuery = """
            SELECT count(*)
              FROM watch_history h JOIN video_sources v ON v.id = h.video_id
             WHERE h.user_id = :userId AND v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'
               AND (CAST(:pattern AS text) IS NULL
                    OR lower(v.title) LIKE lower(CAST(:pattern AS text)) ESCAPE '\\')
            """,
            nativeQuery = true)
    Page<HistoryRow> findPage(@Param("userId") Long userId, @Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
            SELECT h.video_id AS "videoId", h.position_sec AS "positionSec", h.duration_sec AS "durationSec"
              FROM watch_history h
             WHERE h.user_id = :userId
             ORDER BY h.watched_at DESC, h.video_id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<SummaryRow> findSummary(@Param("userId") Long userId, @Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM watch_history WHERE user_id = :userId AND video_id = :videoId", nativeQuery = true)
    int deleteOne(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Modifying
    @Query(value = "DELETE FROM watch_history WHERE user_id = :userId", nativeQuery = true)
    int deleteAll(@Param("userId") Long userId);
}
