package com.canhlabs.funnyapp.dto.watchhistory;

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
public class WatchSummaryDto {
    private Long videoId;
    private int positionSec;
    private Integer durationSec;
    private boolean completed;
}
