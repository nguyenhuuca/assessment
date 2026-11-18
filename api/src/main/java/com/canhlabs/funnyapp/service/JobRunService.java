package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.JobRunDto;
import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persistent history of scheduled-job runs, the latest {@value #KEEP_PER_JOB} per job (plan-job-run-history). */
public interface JobRunService {

    int KEEP_PER_JOB = 100;

    /** Stores one finished run, then deletes that job's runs beyond the newest {@value #KEEP_PER_JOB}. */
    void record(String jobName, JobTrigger trigger, JobRunResult result, Instant startedAt, Instant finishedAt,
                Throwable error);

    /** The newest run of every job that has one, keyed by job name. */
    Map<String, JobRunDto> latestByJob();

    /** The newest runs of one job, newest first; {@code limit} is clamped to 1..{@value #KEEP_PER_JOB}. */
    List<JobRunDto> recent(String jobName, int limit);
}
