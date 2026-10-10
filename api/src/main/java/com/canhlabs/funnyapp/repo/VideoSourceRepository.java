package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}