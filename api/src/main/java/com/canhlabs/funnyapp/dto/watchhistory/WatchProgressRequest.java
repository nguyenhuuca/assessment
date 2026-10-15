package com.canhlabs.funnyapp.dto.watchhistory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Seconds as reported by the player; the server sanitizes them. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchProgressRequest {
    private Double positionSec;
    private Double durationSec;
}
