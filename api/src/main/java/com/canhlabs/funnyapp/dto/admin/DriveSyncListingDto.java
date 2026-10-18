package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSyncListingDto {
    private List<DriveSyncFileDto> files;
    private List<DriveSyncOrphanDto> orphans;
    /** False when a time range was given: orphans need the whole folder, so the list is empty and not meaningful. */
    private boolean orphansChecked;
    /** Max file ids accepted by one force sync run. */
    private int maxBatch;
}
