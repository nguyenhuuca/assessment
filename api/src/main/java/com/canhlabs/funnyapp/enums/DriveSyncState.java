package com.canhlabs.funnyapp.enums;

/** Where a Drive folder video stands against the local cache file and its video_sources row. */
public enum DriveSyncState {
    SYNCED, MISSING_LOCAL, MISSING_DB, MISSING_BOTH, SIZE_MISMATCH, SYNCING
}
