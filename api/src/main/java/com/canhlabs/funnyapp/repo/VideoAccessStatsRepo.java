package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoAccessStats;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoAccessStatsRepo extends JpaRepository<VideoAccessStats, UUID> {
    @Query("SELECT v FROM VideoAccessStats v WHERE v.lastAccessedAt < :before ORDER BY v.hitCount ASC, v.lastAccessedAt ASC")
    List<VideoAccessStats> findLeastAccessed(@Param("before") Instant before, Pageable pageable);
    Optional<VideoAccessStats> findByVideoId(String videoId);
}
