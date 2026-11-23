package com.canhlabs.funnyapp.enums;

/** Outcome of one scheduled-job run, stored in {@code job_run.result}. */
public enum JobRunResult {
    SUCCESS,
    FAILED,
    /** The previous run of the same job was still running, so this one did nothing. */
    SKIPPED
}
