package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One finished run of a scheduled job (plan-job-run-history). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_run")
public class JobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger", nullable = false, length = 16)
    private JobTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private JobRunResult result;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    /** Exception class and message with secrets masked; null unless FAILED. */
    @Column(name = "error", length = 512)
    private String error;
}
