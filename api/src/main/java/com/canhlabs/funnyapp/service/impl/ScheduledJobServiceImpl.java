package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.UpdateScheduledJobRequest;
import com.canhlabs.funnyapp.entity.ScheduledJob;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.jobs.DynamicJobScheduler;
import com.canhlabs.funnyapp.repo.ScheduledJobRepository;
import com.canhlabs.funnyapp.service.ScheduledJobService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ScheduledJobServiceImpl implements ScheduledJobService {

    static final int MAX_CRON_LENGTH = 64;
    static final int MAX_ZONE_LENGTH = 64;
    static final int MAX_PREVIEW = 10;
    /** Seconds must be one number, so a job runs at most once per minute. */
    private static final Pattern SECONDS = Pattern.compile("[0-5]?\\d");

    private final ScheduledJobRepository repository;
    private final DynamicJobScheduler scheduler;

    public ScheduledJobServiceImpl(ScheduledJobRepository repository, DynamicJobScheduler scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledJobDto> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(ScheduledJob::getName))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ScheduledJobDto update(String name, UpdateScheduledJobRequest request) {
        ScheduledJob job = find(name);
        if (request == null || (request.getCron() == null && request.getZone() == null && request.getEnabled() == null)) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.emptyUpdate");
        }
        if (request.getCron() != null) {
            validateCron(request.getCron());
            job.setCron(request.getCron());
        }
        if (request.getZone() != null) {
            job.setZone(request.getZone().isEmpty() ? null : validateZone(request.getZone()));
        }
        if (request.getEnabled() != null) {
            job.setEnabled(request.getEnabled());
        }
        ScheduledJob saved = repository.saveAndFlush(job);
        // a throw here rolls the row back, so the DB and the live schedule stay the same
        scheduler.reschedule(saved);
        return toDto(saved);
    }

    @Override
    public List<Instant> preview(String cron, String zone, int count) {
        if (cron == null) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.invalidCron");
        }
        validateCron(cron);
        ZoneId zoneId = zone == null || zone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(validateZone(zone));
        CronExpression expression = CronExpression.parse(cron);
        List<Instant> runs = new ArrayList<>();
        ZonedDateTime next = ZonedDateTime.now(zoneId);
        while (runs.size() < Math.clamp(count, 1, MAX_PREVIEW)
                && (next = expression.next(next)) != null) {
            runs.add(next.toInstant());
        }
        return runs;
    }

    @Override
    public ScheduledJobDto runNow(String name) {
        ScheduledJob job = find(name);
        if (!scheduler.runNow(name)) {
            throw CustomException.of(HttpStatus.CONFLICT, 409, "error.job.running");
        }
        return toDto(job);
    }

    private ScheduledJob find(String name) {
        return repository.findById(name)
                .filter(j -> scheduler.hasCode(j.getName()))
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 404, "error.job.notFound"));
    }

    private static void validateCron(String cron) {
        if (cron.length() > MAX_CRON_LENGTH || !CronExpression.isValidExpression(cron)) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.invalidCron");
        }
        String[] fields = cron.trim().split("\\s+");
        if (fields.length != 6) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.invalidCron");
        }
        if (!SECONDS.matcher(fields[0]).matches() || Integer.parseInt(fields[0]) > 59) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.tooFrequent");
        }
    }

    private static String validateZone(String zone) {
        if (zone.length() > MAX_ZONE_LENGTH) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.invalidZone");
        }
        try {
            ZoneId.of(zone);
            return zone;
        } catch (DateTimeException e) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.job.invalidZone");
        }
    }

    private ScheduledJobDto toDto(ScheduledJob job) {
        DynamicJobScheduler.JobRun last = scheduler.lastRun(job.getName());
        return ScheduledJobDto.builder()
                .name(job.getName())
                .description(job.getDescription())
                .cron(job.getCron())
                .zone(job.getZone())
                .enabled(job.isEnabled())
                .nextRunAt(job.isEnabled() ? nextRun(job) : null)
                .lastRunAt(last == null ? null : last.at())
                .lastResult(last == null ? null : last.result())
                .running(scheduler.isRunning(job.getName()))
                .build();
    }

    /** A corrupt row (edited by hand in the DB) shows no next run instead of failing the whole list. */
    private static Instant nextRun(ScheduledJob job) {
        try {
            ZoneId zone = job.getZone() == null ? ZoneId.systemDefault() : ZoneId.of(job.getZone());
            ZonedDateTime next = CronExpression.parse(job.getCron()).next(ZonedDateTime.now(zone));
            return next == null ? null : next.toInstant();
        } catch (IllegalArgumentException | DateTimeException e) {
            return null;
        }
    }
}
