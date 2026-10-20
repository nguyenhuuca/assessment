package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Type and autoLabel are strings so an unknown value gets the report's own 400 code, not a JSON error. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    private String type;
    private String title;
    private String description;
    private String autoLabel;
    private ReportContext context;
}
