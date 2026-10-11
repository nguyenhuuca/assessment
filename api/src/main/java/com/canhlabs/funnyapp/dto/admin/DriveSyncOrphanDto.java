package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A video_sources row whose Drive file is no longer in the folder. Shown only, no action. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSyncOrphanDto {
    private Long videoSourceId;
    private String sourceId;
    private String title;
}
