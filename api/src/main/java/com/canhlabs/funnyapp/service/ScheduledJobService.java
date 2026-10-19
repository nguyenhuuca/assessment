package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.UpdateScheduledJobRequest;

import java.util.List;

public interface ScheduledJobService {
    List<ScheduledJobDto> list();

    ScheduledJobDto update(String name, UpdateScheduledJobRequest request);

    /** Starts a manual run (works on a disabled job); 409 when it is already running. */
    ScheduledJobDto runNow(String name);
}
