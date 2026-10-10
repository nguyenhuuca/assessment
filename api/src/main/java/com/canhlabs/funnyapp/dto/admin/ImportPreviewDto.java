package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.ImportPlatform;
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
public class ImportPreviewDto {
    private String title;
    private ImportPlatform platform;
    private Long durationSec;
}
