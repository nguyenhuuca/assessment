package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.domain.VideoSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {

    List<VideoSource> findByVideoId(Long videoId);

    Optional<VideoSource> findFirstByVideoIdAndSourceType(Long videoId, String sourceType);

    boolean existsBySourceId(String sourceId);

    Optional<VideoSource> findBySourceId(String sourceId);
    List<VideoSource> findAllByOrderByCreatedAtDesc();
    List<VideoSource> findAllByDescIsNullOrDesc(String desc);
    List<VideoSource> findAllByIsHideOrderByCreatedAtDesc(boolean hide);


}