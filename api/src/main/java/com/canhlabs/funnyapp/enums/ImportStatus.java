package com.canhlabs.funnyapp.enums;

import java.util.List;

public enum ImportStatus {
    PENDING, DOWNLOADING, UPLOADING, DONE, FAILED, CANCELLED;

    /** Statuses that occupy the "one active import per URL" slot. */
    public static final List<ImportStatus> ACTIVE = List.of(PENDING, DOWNLOADING, UPLOADING);
}
