package com.canhlabs.funnyapp.jobs;

import com.canhlabs.funnyapp.entity.ScheduledJob;
import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;
import com.canhlabs.funnyapp.repo.ScheduledJobRepository;
import com.canhlabs.funnyapp.service.JobRunService;
import com.canhlabs.funnyapp.service.JobScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules the {@link JobRegistry} jobs from the {@code scheduled_job} table (ADR-0023).
 * One replica only: a change applies to this pod and persists for the next start.
 * Every run, including a skipped one, is recorded through {@link JobRunService} (plan-job-run-history).
 */
@Slf4j
@Component
public class DynamicJobScheduler implements JobScheduler {

    private final TaskScheduler taskScheduler;
    private final ScheduledJobRepository repository;
    private final JobRegistry registry;
    private final JobRunService runs;

    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public DynamicJobScheduler(TaskScheduler taskScheduler, ScheduledJobRepository repository, JobRegistry registry,
                               JobRunService runs) {
        this.taskScheduler = taskScheduler;
        this.repository = repository;
        this.registry = registry;
        this.runs = runs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAll() {
        List<ScheduledJob> rows;
        try {
            rows = repository.findAll();
        } catch (Exception e) {
            log.error("Cannot load scheduled_job rows; no job is scheduled", e);
            return;
        }
        if (rows.isEmpty()) {
            log.error("scheduled_job has no rows; no job is scheduled");
            return;
        }
        for (ScheduledJob row : rows) {
            if (registry.get(row.getName()) == null) {
                log.warn("scheduled_job row '{}' has no code in JobRegistry; ignored", row.getName());
                continue;
            }
            reschedule(row);
        }
        for (String name : registry.names()) {
            if (rows.stream().noneMatch(r -> r.getName().equals(name))) {
                log.warn("Job '{}' has code but no scheduled_job row; not scheduled", name);
            }
        }
    }

    @Override
    public void reschedule(ScheduledJob job) {
        String name = job.getName();
        Runnable task = registry.get(name);
        futures.compute(name, (key, old) -> {
            if (old != null) {
                old.cancel(false);
            }
            if (!job.isEnabled() || task == null) {
                return null;
            }
            ZoneId zone = job.getZone() == null ? ZoneId.systemDefault() : ZoneId.of(job.getZone());
            log.info("Scheduling job '{}' cron='{}' zone={}", name, job.getCron(), zone);
            return taskScheduler.schedule(() -> execute(name, task, JobTrigger.SCHEDULE), new CronTrigger(job.getCron(), zone));
        });
    }

    @Override
    public boolean runNow(String name) {
        Runnable task = registry.get(name);
        if (task == null || guard(name).get()) {
            return false;
        }
        taskScheduler.schedule(() -> execute(name, task, JobTrigger.MANUAL), Instant.now());
        return true;
    }

    @Override
    public boolean isRunning(String name) {
        AtomicBoolean flag = running.get(name);
        return flag != null && flag.get();
    }

    @Override
    public boolean hasCode(String name) {
        return registry.get(name) != null;
    }

    private void execute(String name, Runnable task, JobTrigger trigger) {
        Instant start = Instant.now();
        AtomicBoolean flag = guard(name);
        if (!flag.compareAndSet(false, true)) {
            log.warn("Job '{}' is still running; this run is skipped", name);
            record(name, trigger, JobRunResult.SKIPPED, start, null);
            return;
        }
        JobRunResult result = JobRunResult.SUCCESS;
        Exception error = null;
        try {
            task.run();
        } catch (Exception e) {
            result = JobRunResult.FAILED;
            error = e;
            log.error("Job '{}' failed", name, e);
        } finally {
            flag.set(false);
        }
        record(name, trigger, result, start, error);
    }

    /** History is best effort: a failed write is logged and never fails the job or the scheduler thread. */
    private void record(String name, JobTrigger trigger, JobRunResult result, Instant start, Exception error) {
        try {
            runs.record(name, trigger, result, start, Instant.now(), error);
        } catch (RuntimeException e) {
            log.warn("Cannot record run of job '{}' result={}: {}", name, result, e.toString());
        }
    }

    private AtomicBoolean guard(String name) {
        return running.computeIfAbsent(name, k -> new AtomicBoolean());
    }
}
