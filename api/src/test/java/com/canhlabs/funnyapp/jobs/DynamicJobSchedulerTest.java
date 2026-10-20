package com.canhlabs.funnyapp.jobs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.canhlabs.funnyapp.entity.ScheduledJob;
import com.canhlabs.funnyapp.repo.ScheduledJobRepository;
import com.canhlabs.funnyapp.service.notification.NotificationDigestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicJobSchedulerTest {
    @Mock TaskScheduler taskScheduler;
    @Mock ScheduledJobRepository repository;
    @Mock JobRegistry registry;
    @Mock ScheduledFuture<?> future;

    DynamicJobScheduler scheduler;
    ListAppender<ILoggingEvent> logs;
    Logger logger;

    @BeforeEach
    void setUp() {
        scheduler = new DynamicJobScheduler(taskScheduler, repository, registry);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        logger = (Logger) LoggerFactory.getLogger(DynamicJobScheduler.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static ScheduledJob job(String name, boolean enabled) {
        ScheduledJob j = new ScheduledJob();
        j.setName(name);
        j.setCron("0 */15 * * * *");
        j.setZone("Asia/Ho_Chi_Minh");
        j.setEnabled(enabled);
        return j;
    }

    private boolean logged(Level level, String fragment) {
        return logs.list.stream().anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment));
    }

    /** The Runnable handed to the cron-trigger overload of TaskScheduler.schedule. */
    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Trigger.class));
        return captor.getValue();
    }

    // ── (a) startup ───────────────────────────────────────────────────────────

    @Test
    void scheduleAll_schedulesOnlyEnabledRowsWithCode() {
        when(registry.get("a")).thenReturn(() -> { });
        when(registry.get("b")).thenReturn(() -> { });
        when(registry.names()).thenReturn(Set.of("a", "b"));
        when(repository.findAll()).thenReturn(List.of(job("a", true), job("b", false)));

        scheduler.scheduleAll();

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void scheduleAll_warnsForRowWithoutCodeAndCodeWithoutRow() {
        when(registry.get("a")).thenReturn(() -> { });
        when(registry.get("ghost")).thenReturn(null);
        when(registry.names()).thenReturn(Set.of("a", "orphan"));
        when(repository.findAll()).thenReturn(List.of(job("a", true), job("ghost", true)));

        scheduler.scheduleAll();

        assertThat(logged(Level.WARN, "'ghost' has no code")).isTrue();
        assertThat(logged(Level.WARN, "'orphan' has code but no scheduled_job row")).isTrue();
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    // ── (e) startup failures ──────────────────────────────────────────────────

    @Test
    void scheduleAll_loadFails_logsErrorAndDoesNotThrow() {
        when(repository.findAll()).thenThrow(new IllegalStateException("db down"));

        scheduler.scheduleAll();

        assertThat(logged(Level.ERROR, "Cannot load")).isTrue();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void scheduleAll_noRows_logsError() {
        when(repository.findAll()).thenReturn(List.of());

        scheduler.scheduleAll();

        assertThat(logged(Level.ERROR, "no rows")).isTrue();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    // ── (b) reschedule ────────────────────────────────────────────────────────

    @Test
    void reschedule_cancelsOldFutureBeforeSchedulingNew() {
        when(registry.get("a")).thenReturn(() -> { });
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        doReturn(future, second).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        scheduler.reschedule(job("a", true));
        scheduler.reschedule(job("a", true));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(future, taskScheduler);
        order.verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        order.verify(future).cancel(false);
        order.verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void reschedule_disabledJobEndsWithNoFuture() {
        when(registry.get("a")).thenReturn(() -> { });

        scheduler.reschedule(job("a", true));
        scheduler.reschedule(job("a", false));

        verify(future).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void reschedule_zoneNullUsesDefaultZone() {
        when(registry.get("a")).thenReturn(() -> { });
        ScheduledJob j = job("a", true);
        j.setZone(null);

        scheduler.reschedule(j);

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    // ── (c) overlap guard ─────────────────────────────────────────────────────

    @Test
    void run_whileRunning_isSkipped() {
        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean nowAccepted = new AtomicBoolean(true);
        Runnable[] self = new Runnable[1];
        when(registry.get("a")).thenReturn(() -> {
            runs.incrementAndGet();
            self[0].run(); // a second fire while this one is in progress
            nowAccepted.set(scheduler.runNow("a"));
            assertThat(scheduler.isRunning("a")).isTrue();
        });
        scheduler.reschedule(job("a", true));
        self[0] = scheduledTask();

        self[0].run();

        assertThat(runs).hasValue(1);
        assertThat(nowAccepted).isFalse();
        assertThat(logged(Level.WARN, "still running")).isTrue();
        assertThat(scheduler.isRunning("a")).isFalse();
    }

    @Test
    void run_guardReleasedAfterException() {
        AtomicInteger runs = new AtomicInteger();
        when(registry.get("a")).thenReturn(() -> {
            runs.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        scheduler.reschedule(job("a", true));
        Runnable task = scheduledTask();

        task.run();
        task.run();

        assertThat(runs).hasValue(2);
        assertThat(scheduler.isRunning("a")).isFalse();
    }

    // ── (d) result recording ──────────────────────────────────────────────────

    @Test
    void run_exceptionIsCaughtAndRecordedAsFailed() {
        when(registry.get("a")).thenReturn(() -> {
            throw new IllegalStateException("boom");
        });
        scheduler.reschedule(job("a", true));

        scheduledTask().run();

        assertThat(scheduler.lastRun("a").result()).isEqualTo("FAILED");
        assertThat(scheduler.lastRun("a").at()).isNotNull();
    }

    @Test
    void run_realAppSchedulerJobThatFails_isRecordedAsFailed() {
        NotificationDigestService digestService = mock(NotificationDigestService.class);
        when(digestService.sendDigests()).thenThrow(new IllegalStateException("smtp down"));
        AppScheduler appScheduler = new AppScheduler(null, null, null, null, null, null, null, null, digestService);
        when(registry.get("notification-digest")).thenReturn(appScheduler::notificationDigest);
        scheduler.reschedule(job("notification-digest", true));

        scheduledTask().run();

        assertThat(scheduler.lastRun("notification-digest").result()).isEqualTo("FAILED");
    }

    @Test
    void run_successIsRecorded() {
        when(registry.get("a")).thenReturn(() -> { });
        scheduler.reschedule(job("a", true));

        scheduledTask().run();

        assertThat(scheduler.lastRun("a").result()).isEqualTo("SUCCESS");
        assertThat(scheduler.lastRun("other")).isNull();
    }

    // ── run now ───────────────────────────────────────────────────────────────

    @Test
    void runNow_schedulesImmediateRunEvenWhenDisabled() {
        AtomicInteger runs = new AtomicInteger();
        when(registry.get("a")).thenReturn(runs::incrementAndGet);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        assertThat(scheduler.runNow("a")).isTrue();

        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        captor.getValue().run();
        assertThat(runs).hasValue(1);
    }

    @Test
    void runNow_unknownJobIsRejected() {
        when(registry.get("nope")).thenReturn(null);

        assertThat(scheduler.runNow("nope")).isFalse();
        assertThat(scheduler.hasCode("nope")).isFalse();
        assertThat(scheduler.isRunning("nope")).isFalse();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
