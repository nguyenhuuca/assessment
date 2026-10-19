package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.UpdateScheduledJobRequest;

import java.time.Instant;
import java.util.List;

public interface ScheduledJobService {
    List<ScheduledJobDto> list();

    ScheduledJobDto update(String name, UpdateScheduledJobRequest request);

    /** Next runs of a cron the editor is typing, validated with the same rules as {@link #update}. */
    List<Instant> preview(String cron, String zone, int count);

    /** Starts a manual run (works on a disabled job); 409 when it is already running. */
    ScheduledJobDto runNow(String name);
}
