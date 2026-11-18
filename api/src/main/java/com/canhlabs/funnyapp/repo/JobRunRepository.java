package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.JobRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    /** Newest first. */
    List<JobRun> findByJobNameOrderByStartedAtDescIdDesc(String jobName, Pageable pageable);

    /** The newest run of every job, in one query (Admin → Jobs list). */
    @Query(value = "SELECT DISTINCT ON (job_name) * FROM job_run ORDER BY job_name, started_at DESC, id DESC",
            nativeQuery = true)
    List<JobRun> findLatestPerJob();

    /** Deletes the runs of one job beyond its newest {@code keep}; returns the number deleted. */
    @Modifying
    @Query(value = "DELETE FROM job_run WHERE job_name = :name AND id NOT IN ("
            + "SELECT id FROM job_run WHERE job_name = :name ORDER BY started_at DESC, id DESC LIMIT :keep)",
            nativeQuery = true)
    int deleteOlderThanNewest(@Param("name") String jobName, @Param("keep") int keep);
}
