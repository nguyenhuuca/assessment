package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledJobDto {
    private String name;
    private String description;
    private String cron;
    /** Null = JVM default zone. */
    private String zone;
    private boolean enabled;
    /** Null when disabled or the cron has no future date. */
    private Instant nextRunAt;
    /** Null until the job has run since the last restart. */
    private Instant lastRunAt;
    /** SUCCESS or FAILED; null until the job has run since the last restart. */
    private String lastResult;
    private boolean running;
}
