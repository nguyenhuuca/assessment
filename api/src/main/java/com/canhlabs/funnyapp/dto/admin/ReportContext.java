package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Where the reporter was when they sent the report; every field is optional. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContext {
    private String page;
    private String path;
    private String appVersion;
    private String userAgent;
    private Integer screenWidth;
}
