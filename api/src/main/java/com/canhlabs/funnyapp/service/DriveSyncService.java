package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;

import java.util.List;

/** Admin force sync between the Drive folder, the local video cache and video_sources. */
public interface DriveSyncService {

    int MAX_BATCH = 50;

    /** Every file in the Drive folder with its sync state, plus DB rows whose Drive file is gone. */
    DriveSyncListingDto listFolder();

    /**
     * Starts a background run that re-downloads the given files and upserts their rows.
     * Ids must be in the Drive folder; only one run at a time.
     */
    DriveSyncRunDto startRun(List<String> fileIds);

    /** The running run, or the last finished one; null if none since startup. */
    DriveSyncRunDto currentRun();
}
