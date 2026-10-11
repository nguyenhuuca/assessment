package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.DriveSyncItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSyncItemDto {
    private String fileId;
    private String name;
    private DriveSyncItemStatus status;
    private long downloadedBytes;
    /** Drive size; 0 when unknown. */
    private long totalBytes;
    /** DISK_FULL, DRIVE_ERROR, DB_ERROR, INTERNAL; null unless FAILED. */
    private String errorCode;
}
