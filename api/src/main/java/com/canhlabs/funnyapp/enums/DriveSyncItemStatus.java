package com.canhlabs.funnyapp.enums;

public enum DriveSyncItemStatus {
    QUEUED, DOWNLOADING, SAVING, DONE, FAILED, SKIPPED_BUSY;

    public boolean isFinished() {
        return this == DONE || this == FAILED || this == SKIPPED_BUSY;
    }
}
