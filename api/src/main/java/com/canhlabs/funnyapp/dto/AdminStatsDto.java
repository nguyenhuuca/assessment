package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {
    private long totalVideos;
    private long totalUsers;
    private long pendingCount;
    private long flaggedCount;
}
