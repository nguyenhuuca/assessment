package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.HlsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Result of the HLS toggle endpoints: the video's HLS state after the call. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminHlsStatusDto {
    private Long id;
    private HlsStatus hlsStatus;
    private String hlsError;
}
