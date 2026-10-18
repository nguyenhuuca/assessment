package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;

import java.time.Instant;
import java.util.List;

/** Admin force sync between the Drive folder, the local video cache and video_sources. */
public interface DriveSyncService {

    int MAX_BATCH = 50;

    /**
     * Files in the Drive folder with their sync state. Without a range this is the whole folder plus the DB rows
     * whose Drive file is gone. With {@code from} (inclusive) and/or {@code to} (exclusive) on the Drive created
     * time only the files in range are listed and orphans are not computed ({@code orphansChecked = false}).
     */
    DriveSyncListingDto listFolder(Instant from, Instant to);

    /**
     * Starts a background run that re-downloads the given files and upserts their rows.
     * Ids must be in the Drive folder; only one run at a time.
     */
    DriveSyncRunDto startRun(List<String> fileIds);

    /** The running run, or the last finished one; null if none since startup. */
    DriveSyncRunDto currentRun();
}
