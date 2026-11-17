package com.canhlabs.funnyapp.service.backup;

/** Failure classification for a backup run. */
public enum BackupErrorCode {
    NOT_CONFIGURED,
    ALREADY_RUNNING,
    PG_DUMP_NOT_FOUND,
    PG_DUMP_TIMEOUT,
    PG_DUMP_FAILED,
    DUMP_INVALID,
    DRIVE_AUTH_FAILED,
    DRIVE_FOLDER_NOT_FOUND,
    DRIVE_UPLOAD_FAILED,
    /** Warning only: the backup itself succeeded. */
    PRUNE_FAILED,
    INTERNAL_ERROR
}
