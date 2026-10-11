package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSource;
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

    boolean existsByIdAndIsHide(Long id, boolean isHide);

    Optional<VideoSource> findBySourceId(String sourceId);
    List<VideoSource> findAllByOrderByCreatedAtDesc();
    List<VideoSource> findAllByDescIsNullOrDesc(String desc);
    List<VideoSource> findAllByIsHideOrderByPriorityDescCreatedAtDesc(boolean hide);

    Page<VideoSource> findAllByStatus(VideoStatus status, Pageable pageable);

    long countByStatus(VideoStatus status);

    /** Atomic increment in the DB; hidden videos are not counted. Returns rows updated (0 = unknown or hidden). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE video_sources SET view_count = view_count + 1 WHERE id = :id AND is_hide = false", nativeQuery = true)
    int incrementViewCount(@Param("id") Long id);

    List<VideoSource> findAllBySourceType(String sourceType);

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

}