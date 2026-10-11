package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSyncRunDto {
    private String runId;
    private Instant startedAt;
    /** Null while the run is active. */
    private Instant finishedAt;
    private boolean active;
    private List<DriveSyncItemDto> items;
}
