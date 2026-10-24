package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {

    List<VideoSource> findByVideoId(Long videoId);

    Optional<VideoSource> findFirstByVideoIdAndSourceType(Long videoId, String sourceType);

    boolean existsBySourceId(String sourceId);

    /** Batched lookup: which of the given source ids already have a video_sources row. */
    @Query("select v.sourceId from VideoSource v where v.sourceId in :ids")
    List<String> findSourceIdsIn(@Param("ids") Collection<String> ids);

    /** Public visibility: not hidden by admin and moderation status PUBLISHED. */
    boolean existsByIdAndIsHideAndStatus(Long id, boolean isHide, VideoStatus status);

    Optional<VideoSource> findBySourceId(String sourceId);
    List<VideoSource> findAllByOrderByCreatedAtDesc();
    List<VideoSource> findAllByDescIsNullOrDesc(String desc);
    List<VideoSource> findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc(boolean hide, VideoStatus status);

    Page<VideoSource> findAllByStatus(VideoStatus status, Pageable pageable);

    long countByStatus(VideoStatus status);

    /** Atomic increment in the DB; hidden videos are not counted. Returns rows updated (0 = unknown or hidden). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET view_count = view_count + 1 WHERE id = :id AND is_hide = false", nativeQuery = true)
    int incrementViewCount(@Param("id") Long id);

    List<VideoSource> findAllBySourceType(String sourceType);

    List<VideoSource> findAllBySourceTypeAndSourceIdIn(String sourceType, Collection<String> sourceIds);

    /**
     * Force sync: insert the Drive video, or refresh title + thumbnail of the existing row.
     * Moderation (is_hide, status, priority), view_count and an existing description are kept.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO video_sources (video_id, source_type, source_id, title, description, credentials_ref,
                                       thumbnail_path, is_hide, status, priority, created_at, updated_at)
            VALUES (:videoId, 'google_drive', :sourceId, :title, :desc, '', :thumbnailPath,
                    false, 'PUBLISHED', 0, now(), now())
            ON CONFLICT (source_type, source_id) DO UPDATE
               SET title          = EXCLUDED.title,
                   thumbnail_path = EXCLUDED.thumbnail_path,
                   description    = COALESCE(video_sources.description, EXCLUDED.description),
                   updated_at     = now()
            """, nativeQuery = true)
    int upsertDriveSource(@Param("videoId") Long videoId, @Param("sourceId") String sourceId,
                          @Param("title") String title, @Param("desc") String desc,
                          @Param("thumbnailPath") String thumbnailPath);

    List<VideoSource> findAllByHlsStatus(HlsStatus hlsStatus);

    /** PENDING HLS jobs, oldest first (BR-4). */
    @Query(value = "SELECT * FROM video_sources WHERE hls_status = 'PENDING' ORDER BY updated_at, id", nativeQuery = true)
    List<VideoSource> findHlsQueue();

    /**
     * Atomic claim: PENDING -> PROCESSING. Returns 1 when this caller won the row, 0 otherwise (EC-8).
     * updated_at is set by hand: native updates do not fire @UpdateTimestamp.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET hls_status = 'PROCESSING', updated_at = now() "
            + "WHERE id = :id AND hls_status = 'PENDING'", nativeQuery = true)
    int claimHls(@Param("id") Long id);

    /** Publish step: PROCESSING -> READY only while the row is still PROCESSING (a concurrent toggle wins). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET hls_status = 'READY', hls_error = NULL, updated_at = now() "
            + "WHERE id = :id AND hls_status = 'PROCESSING'", nativeQuery = true)
    int markHlsReady(@Param("id") Long id);

    /** Failure step: PROCESSING -> ERROR only while the row is still PROCESSING (never over NONE or PENDING). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET hls_status = 'ERROR', hls_error = :code, updated_at = now() "
            + "WHERE id = :id AND hls_status = 'PROCESSING'", nativeQuery = true)
    int markHlsError(@Param("id") Long id, @Param("code") String code);

    /** Startup check (BR-11): READY -> ERROR OUTPUT_MISSING only while the row is still READY. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET hls_status = 'ERROR', hls_error = 'OUTPUT_MISSING', updated_at = now() "
            + "WHERE id = :id AND hls_status = 'READY'", nativeQuery = true)
    int markHlsOutputMissing(@Param("id") Long id);

    /** Startup recovery (BR-11): jobs interrupted by a restart go back to the queue. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET hls_status = 'PENDING', updated_at = now() "
            + "WHERE hls_status = 'PROCESSING'", nativeQuery = true)
    int resetHlsProcessing();

}