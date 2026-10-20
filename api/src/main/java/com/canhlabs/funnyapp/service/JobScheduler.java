package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.entity.ScheduledJob;

import java.time.Instant;

/**
 * Live schedule of the {@code scheduled_job} rows (ADR-0023). Implemented in the jobs layer
 * ({@code DynamicJobScheduler}), so the service layer does not depend on it.
 */
public interface JobScheduler {

    /** In-memory outcome of the latest run; reset on restart. */
    record JobRun(Instant at, String result) {
    }

    /** Replaces the live schedule of one job: cancels the old future, then schedules a new one if enabled. */
    void reschedule(ScheduledJob job);

    /** Starts a run now; false when the job is already running or has no code. */
    boolean runNow(String name);

    boolean isRunning(String name);

    boolean hasCode(String name);

    JobRun lastRun(String name);
}
