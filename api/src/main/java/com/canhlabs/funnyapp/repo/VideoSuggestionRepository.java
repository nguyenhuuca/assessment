package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSuggestion;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoSuggestionRepository extends JpaRepository<VideoSuggestion, Long> {

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    boolean existsByNormalizedUrlAndStatus(String normalizedUrl, SuggestionStatus status);

    long countByStatus(SuggestionStatus status);

    Optional<VideoSuggestion> findFirstByImportJobId(Long importJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VideoSuggestion s where s.id = :id")
    Optional<VideoSuggestion> findByIdForUpdate(@Param("id") Long id);

    // user's own list (the caller supplies newest-first sorting through the Pageable)

    Page<VideoSuggestion> findByUserId(Long userId, Pageable pageable);

    Page<VideoSuggestion> findByUserIdAndStatus(Long userId, SuggestionStatus status, Pageable pageable);

    /** Approved and the job's Drive file is already in video_sources. */
    @Query(value = """
            select s from VideoSuggestion s
             where s.userId = :userId and s.status = com.canhlabs.funnyapp.enums.SuggestionStatus.APPROVED
               and exists (select 1 from VideoImportJob j, VideoSource v
                            where j.id = s.importJobId and v.sourceId = j.driveFileId)
            """,
            countQuery = """
            select count(s) from VideoSuggestion s
             where s.userId = :userId and s.status = com.canhlabs.funnyapp.enums.SuggestionStatus.APPROVED
               and exists (select 1 from VideoImportJob j, VideoSource v
                            where j.id = s.importJobId and v.sourceId = j.driveFileId)
            """)
    Page<VideoSuggestion> findLiveByUserId(@Param("userId") Long userId, Pageable pageable);

    // admin list

    @Query(value = """
            select s from VideoSuggestion s
             order by case when s.status = com.canhlabs.funnyapp.enums.SuggestionStatus.PENDING then 0 else 1 end,
                      s.createdAt desc, s.id desc
            """,
            countQuery = "select count(s) from VideoSuggestion s")
    Page<VideoSuggestion> findAllPendingFirst(Pageable pageable);

    @Query(value = """
            select s from VideoSuggestion s where s.status = :status
             order by s.createdAt desc, s.id desc
            """,
            countQuery = "select count(s) from VideoSuggestion s where s.status = :status")
    Page<VideoSuggestion> findAllByStatusNewest(@Param("status") SuggestionStatus status, Pageable pageable);

    @Query(value = """
            select s from VideoSuggestion s
             where s.status = com.canhlabs.funnyapp.enums.SuggestionStatus.APPROVED
               and exists (select 1 from VideoImportJob j, VideoSource v
                            where j.id = s.importJobId and v.sourceId = j.driveFileId)
             order by s.createdAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from VideoSuggestion s
             where s.status = com.canhlabs.funnyapp.enums.SuggestionStatus.APPROVED
               and exists (select 1 from VideoImportJob j, VideoSource v
                            where j.id = s.importJobId and v.sourceId = j.driveFileId)
            """)
    Page<VideoSuggestion> findAllLiveNewest(Pageable pageable);

    /** One batch lookup for a whole page: which suggestions already have a video_sources row. */
    @Query("""
            select new com.canhlabs.funnyapp.repo.SuggestionLiveRef(s.id, v.id)
              from VideoSuggestion s, VideoImportJob j, VideoSource v
             where s.id in :ids and j.id = s.importJobId and v.sourceId = j.driveFileId
            """)
    List<SuggestionLiveRef> findLiveRefs(@Param("ids") Collection<Long> ids);
}
