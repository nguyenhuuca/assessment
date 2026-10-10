package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoImportJobDto {
    private Long id;
    private String sourceUrl;
    private ImportPlatform platform;
    private String title;
    private ImportStatus status;
    /** QUEUED, DOWNLOAD, UPLOAD, DONE, or null for FAILED / CANCELLED. */
    private String phase;
    /** Percent of the current phase (download 0-100, then upload 0-100). */
    private int progressPct;
    private long downloadedBytes;
    private long totalBytes;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String driveFileId;
    /** True once the Drive cron created the video_sources row for driveFileId. */
    private boolean ingested;
    private String errorCode;
    private String errorMessage;
    private int attempts;
    private Instant createdAt;
}
