package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.enums.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

public interface VideoImportJobRepository extends JpaRepository<VideoImportJob, Long> {

    Page<VideoImportJob> findByStatus(ImportStatus status, Pageable pageable);

    boolean existsByNormalizedUrlAndStatusIn(String normalizedUrl, Collection<ImportStatus> statuses);

    /**
     * Atomically takes the oldest due PENDING job and flips it to DOWNLOADING.
     * "Due" allows 1 minute of slack: the DB is on another host, and a 09:05 job must not miss the
     * 09:05 tick because the DB clock is a few ms behind (next tick would be 09:10).
     * FOR UPDATE SKIP LOCKED makes concurrent claimers pick different rows.
     */
    @Transactional
    @Query(value = """
            UPDATE video_import_jobs
               SET status = 'DOWNLOADING', started_at = now(), updated_at = now(),
                   attempts = attempts + 1, progress_pct = 0, downloaded_bytes = 0, total_bytes = 0,
                   error_code = NULL, error_message = NULL, finished_at = NULL
             WHERE id = (SELECT id FROM video_import_jobs
                          WHERE status = 'PENDING' AND scheduled_at <= now() + interval '1 minute'
                          ORDER BY scheduled_at, id
                          LIMIT 1
                          FOR UPDATE SKIP LOCKED)
            RETURNING id
            """, nativeQuery = true)
    Optional<Long> claimNextDue();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE video_import_jobs
               SET progress_pct = :pct, downloaded_bytes = :downloaded, total_bytes = :total, updated_at = now()
             WHERE id = :id AND status IN ('DOWNLOADING', 'UPLOADING')
            """, nativeQuery = true)
    int updateProgress(@Param("id") Long id, @Param("pct") int pct,
                       @Param("downloaded") long downloaded, @Param("total") long total);

    /** Startup recovery: interrupted jobs go back to PENDING (attempts below 3) or FAILED (INTERRUPTED). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE video_import_jobs
               SET status = CASE WHEN attempts < 3 THEN 'PENDING' ELSE 'FAILED' END,
                   error_code = CASE WHEN attempts < 3 THEN NULL ELSE 'INTERRUPTED' END,
                   error_message = CASE WHEN attempts < 3 THEN NULL ELSE 'Import was interrupted by a restart' END,
                   finished_at = CASE WHEN attempts < 3 THEN NULL ELSE now() END,
                   progress_pct = 0, downloaded_bytes = 0, total_bytes = 0,
                   updated_at = now()
             WHERE status IN ('DOWNLOADING', 'UPLOADING')
            """, nativeQuery = true)
    int resetInterrupted();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE video_import_jobs
               SET status = 'CANCELLED', finished_at = now(), updated_at = now()
             WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int cancelIfPending(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE video_import_jobs SET scheduled_at = now(), updated_at = now()
             WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int runNowIfPending(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE video_import_jobs
               SET status = 'PENDING', scheduled_at = now(), attempts = 0,
                   progress_pct = 0, downloaded_bytes = 0, total_bytes = 0,
                   started_at = NULL, finished_at = NULL, drive_file_id = NULL,
                   error_code = NULL, error_message = NULL, updated_at = now()
             WHERE id = :id AND status IN ('FAILED', 'CANCELLED')
            """, nativeQuery = true)
    int retryIfFinal(@Param("id") Long id);
}
