package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One run of a scheduled job, as shown in Admin → Jobs history. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRunDto {
    private String jobName;
    /** SCHEDULE or MANUAL. */
    private String trigger;
    /** SUCCESS, FAILED or SKIPPED. */
    private String result;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationMs;
    /** Exception class and message with secrets masked; null unless FAILED. */
    private String error;
}
