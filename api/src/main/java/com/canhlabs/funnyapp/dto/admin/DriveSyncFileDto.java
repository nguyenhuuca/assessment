package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.DriveSyncState;
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
public class DriveSyncFileDto {
    private String fileId;
    private String name;
    /** Drive size; null when Drive does not report one. */
    private Long sizeBytes;
    private Instant createdAt;
    /** Size of video-cache/{fileId}.full; null when the file is missing. */
    private Long localSizeBytes;
    /** video_sources.id; null when there is no row. */
    private Long videoSourceId;
    private String dbTitle;
    private DriveSyncState state;
}
