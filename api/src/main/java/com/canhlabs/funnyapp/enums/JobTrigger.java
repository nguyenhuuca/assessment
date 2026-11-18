package com.canhlabs.funnyapp.enums;

/** What started a scheduled-job run, stored in {@code job_run.trigger}. */
public enum JobTrigger {
    /** Fired by the job's cron. */
    SCHEDULE,
    /** Started from Admin → Jobs "Run now". */
    MANUAL
}
