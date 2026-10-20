package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.UpdateScheduledJobRequest;
import com.canhlabs.funnyapp.entity.ScheduledJob;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.JobScheduler;
import com.canhlabs.funnyapp.repo.ScheduledJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledJobServiceImplTest {
    @Mock ScheduledJobRepository repository;
    @Mock JobScheduler scheduler;

    ScheduledJobServiceImpl service;
    ScheduledJob job;

    @BeforeEach
    void setUp() {
        service = new ScheduledJobServiceImpl(repository, scheduler);
        job = new ScheduledJob();
        job.setName("notification-digest");
        job.setCron("0 */15 * * * *");
        job.setEnabled(true);
        job.setDescription("digest");
        when(repository.findById("notification-digest")).thenReturn(Optional.of(job));
        when(repository.saveAndFlush(any(ScheduledJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scheduler.hasCode("notification-digest")).thenReturn(true);
    }

    private static UpdateScheduledJobRequest req(String cron, String zone, Boolean enabled) {
        return UpdateScheduledJobRequest.builder().cron(cron).zone(zone).enabled(enabled).build();
    }

    private void assertRejected(UpdateScheduledJobRequest request, HttpStatus status, String key) {
        assertThatThrownBy(() -> service.update("notification-digest", request))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getMessageKey()).isEqualTo(key);
                });
        verify(repository, never()).saveAndFlush(any());
        verify(scheduler, never()).reschedule(any());
    }

    @Test
    void update_invalidCron_returns400() {
        assertRejected(req("not a cron", null, null), HttpStatus.BAD_REQUEST, "error.job.invalidCron");
    }

    @Test
    void update_cronLongerThan64_returns400() {
        assertRejected(req("0 " + "1,".repeat(40) + "1 * * * *", null, null),
                HttpStatus.BAD_REQUEST, "error.job.invalidCron");
    }

    @Test
    void update_macroCron_returns400() {
        assertRejected(req("@daily", null, null), HttpStatus.BAD_REQUEST, "error.job.invalidCron");
    }

    @Test
    void update_everySecond_returnsTooFrequent() {
        assertRejected(req("*/5 * * * * *", null, null), HttpStatus.BAD_REQUEST, "error.job.tooFrequent");
        assertRejected(req("* * * * * *", null, null), HttpStatus.BAD_REQUEST, "error.job.tooFrequent");
        assertRejected(req("0,30 * * * * *", null, null), HttpStatus.BAD_REQUEST, "error.job.tooFrequent");
    }

    @Test
    void update_badZone_returns400() {
        assertRejected(req(null, "Mars/Olympus", null), HttpStatus.BAD_REQUEST, "error.job.invalidZone");
    }

    @Test
    void update_zoneLongerThan64_returns400() {
        assertRejected(req(null, "Europe/" + "x".repeat(60), null), HttpStatus.BAD_REQUEST, "error.job.invalidZone");
    }

    @Test
    void list_corruptRow_hasNullNextRunInsteadOfFailing() {
        job.setCron("not a cron");
        ScheduledJob badZone = new ScheduledJob();
        badZone.setName("b-job");
        badZone.setCron("0 0 3 * * *");
        badZone.setZone("Mars/Olympus");
        badZone.setEnabled(true);
        when(repository.findAll()).thenReturn(List.of(job, badZone));

        List<ScheduledJobDto> dtos = service.list();

        assertThat(dtos).hasSize(2).allSatisfy(d -> assertThat(d.getNextRunAt()).isNull());
    }

    @Test
    void preview_returnsNextRunsInOrder() {
        List<Instant> runs = service.preview("0 30 3 * * *", "Asia/Ho_Chi_Minh", 3);

        assertThat(runs).hasSize(3).isSorted();
        assertThat(Duration.between(runs.get(0), runs.get(1))).isEqualTo(Duration.ofDays(1));
        assertThat(runs.get(0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getHour()).isEqualTo(3);
    }

    @Test
    void preview_countIsClampedAndImpossibleDateIsEmpty() {
        assertThat(service.preview("0 */15 * * * *", null, 500)).hasSize(ScheduledJobServiceImpl.MAX_PREVIEW);
        assertThat(service.preview("0 */15 * * * *", "", 0)).hasSize(1);
        assertThat(service.preview("0 0 3 30 2 *", null, 5)).isEmpty();
    }

    @Test
    void preview_appliesTheUpdateRules() {
        assertPreviewRejected(null, null, "error.job.invalidCron");
        assertPreviewRejected("nope", null, "error.job.invalidCron");
        assertPreviewRejected("* * * * * *", null, "error.job.tooFrequent");
        assertPreviewRejected("0 0 3 * * *", "Mars/Olympus", "error.job.invalidZone");
    }

    private void assertPreviewRejected(String cron, String zone, String key) {
        assertThatThrownBy(() -> service.preview(cron, zone, 5))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessageKey()).isEqualTo(key);
                });
    }

    @Test
    void update_emptyBody_returns400() {
        assertRejected(req(null, null, null), HttpStatus.BAD_REQUEST, "error.job.emptyUpdate");
        assertRejected(null, HttpStatus.BAD_REQUEST, "error.job.emptyUpdate");
    }

    @Test
    void update_unknownName_returns404() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("ghost", req("0 0 * * * *", null, null)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessageKey()).isEqualTo("error.job.notFound");
                });
    }

    @Test
    void update_rowWithoutCode_returns404() {
        when(scheduler.hasCode("notification-digest")).thenReturn(false);

        assertThatThrownBy(() -> service.update("notification-digest", req("0 0 * * * *", null, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_validPatch_savesThenReschedules() {
        ScheduledJobDto dto = service.update("notification-digest", req("0 0 4 * * *", "Asia/Ho_Chi_Minh", false));

        InOrder order = inOrder(repository, scheduler);
        order.verify(repository).saveAndFlush(job);
        order.verify(scheduler).reschedule(job);
        assertThat(dto.getCron()).isEqualTo("0 0 4 * * *");
        assertThat(dto.getZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(dto.isEnabled()).isFalse();
        assertThat(dto.getNextRunAt()).isNull();
    }

    @Test
    void update_onlySentFieldsChange() {
        job.setZone("Asia/Ho_Chi_Minh");

        service.update("notification-digest", req(null, null, false));

        assertThat(job.getCron()).isEqualTo("0 */15 * * * *");
        assertThat(job.getZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(job.isEnabled()).isFalse();
    }

    @Test
    void update_emptyZoneClearsToNull() {
        job.setZone("Asia/Ho_Chi_Minh");

        service.update("notification-digest", req(null, "", null));

        assertThat(job.getZone()).isNull();
    }

    @Test
    void update_rescheduleThrows_propagatesSoTransactionRollsBack() {
        doThrow(new IllegalStateException("scheduler down")).when(scheduler).reschedule(any());

        assertThatThrownBy(() -> service.update("notification-digest", req("0 0 4 * * *", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_updateMethodIsTransactional() throws Exception {
        assertThat(ScheduledJobServiceImpl.class
                .getMethod("update", String.class, UpdateScheduledJobRequest.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }

    @Test
    void list_isSortedAndCarriesRunState() {
        ScheduledJob other = new ScheduledJob();
        other.setName("a-job");
        other.setCron("0 0 3 30 2 *"); // 30 Feb: no next run
        other.setEnabled(true);
        when(repository.findAll()).thenReturn(List.of(job, other));
        Instant at = Instant.parse("2026-10-19T00:00:00Z");
        when(scheduler.lastRun("notification-digest")).thenReturn(new JobScheduler.JobRun(at, "FAILED"));
        when(scheduler.isRunning("notification-digest")).thenReturn(true);

        List<ScheduledJobDto> dtos = service.list();

        assertThat(dtos).extracting(ScheduledJobDto::getName).containsExactly("a-job", "notification-digest");
        assertThat(dtos.get(0).getNextRunAt()).isNull();
        assertThat(dtos.get(1).getNextRunAt()).isNotNull();
        assertThat(dtos.get(1).getLastRunAt()).isEqualTo(at);
        assertThat(dtos.get(1).getLastResult()).isEqualTo("FAILED");
        assertThat(dtos.get(1).isRunning()).isTrue();
    }

    @Test
    void runNow_started_returnsDtoEvenWhenDisabled() {
        job.setEnabled(false);
        when(scheduler.runNow("notification-digest")).thenReturn(true);

        ScheduledJobDto dto = service.runNow("notification-digest");

        assertThat(dto.getName()).isEqualTo("notification-digest");
        assertThat(dto.getNextRunAt()).isNull();
    }

    @Test
    void runNow_alreadyRunning_returns409() {
        when(scheduler.runNow("notification-digest")).thenReturn(false);

        assertThatThrownBy(() -> service.runNow("notification-digest"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessageKey()).isEqualTo("error.job.running");
                });
    }

    @Test
    void runNow_unknownName_returns404() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runNow("ghost"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(scheduler, never()).runNow(any());
    }
}
