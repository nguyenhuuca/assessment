package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    private UUID id;
    private ReportType type;
    private String title;
    private ReportStatus status;
    private Integer issueNumber;
    private String issueUrl;
    private List<String> labels;
    private boolean autopilot;
    private ReportAutoLabel autoLabel;
    /** True when a CREATED report went out without autopilot because of the daily cap. */
    private boolean capReached;
    private String error;
    /** Null when the reporter's account was deleted. */
    private String reporter;
    /** OPEN, CLOSED or UNKNOWN for CREATED rows; null for FAILED rows. */
    private String githubState;
    private Instant createdAt;
}
