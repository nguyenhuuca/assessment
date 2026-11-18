package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.JobRunDto;
import com.canhlabs.funnyapp.entity.JobRun;
import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;
import com.canhlabs.funnyapp.repo.JobRunRepository;
import com.canhlabs.funnyapp.service.JobRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRunServiceImplTest {
    private static final Instant START = Instant.parse("2026-11-18T03:00:00Z");
    private static final Instant END = Instant.parse("2026-11-18T03:00:02.500Z");

    @Mock JobRunRepository repository;
    JobRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobRunServiceImpl(repository);
    }

    private JobRun saved() {
        ArgumentCaptor<JobRun> captor = ArgumentCaptor.forClass(JobRun.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_success_savesRowThenPrunesToKeep() {
        service.record("db-backup-daily", JobTrigger.SCHEDULE, JobRunResult.SUCCESS, START, END, null);

        JobRun run = saved();
        assertThat(run.getJobName()).isEqualTo("db-backup-daily");
        assertThat(run.getTrigger()).isEqualTo(JobTrigger.SCHEDULE);
        assertThat(run.getResult()).isEqualTo(JobRunResult.SUCCESS);
        assertThat(run.getStartedAt()).isEqualTo(START);
        assertThat(run.getFinishedAt()).isEqualTo(END);
        assertThat(run.getDurationMs()).isEqualTo(2500);
        assertThat(run.getError()).isNull();
        InOrder order = inOrder(repository);
        order.verify(repository).save(any(JobRun.class));
        order.verify(repository).deleteOlderThanNewest("db-backup-daily", JobRunService.KEEP_PER_JOB);
    }

    @Test
    void record_failure_storesMaskedOneLineError() {
        service.record("sync", JobTrigger.MANUAL, JobRunResult.FAILED, START, START,
                new IllegalStateException("login failed\npassword=hunter2 at line 3"));

        JobRun run = saved();
        assertThat(run.getError()).isEqualTo("IllegalStateException: login failed password=*** at line 3");
        assertThat(run.getDurationMs()).isZero();
    }

    @Test
    void record_clockSkew_durationNeverNegative() {
        service.record("a", JobTrigger.SCHEDULE, JobRunResult.SKIPPED, END, START, null);

        assertThat(saved().getDurationMs()).isZero();
    }

    @Test
    void record_isTransactional() throws Exception {
        assertThat(JobRunServiceImpl.class.getMethod("record", String.class, JobTrigger.class, JobRunResult.class,
                Instant.class, Instant.class, Throwable.class).isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void describe_appendsRootCause() {
        String text = JobRunServiceImpl.describe(
                new UncheckedIOException("Google Drive sync failed", new IOException("403 quota")));

        assertThat(text).isEqualTo("UncheckedIOException: Google Drive sync failed <- IOException: 403 quota");
    }

    @Test
    void describe_nullMessage_usesClassName() {
        assertThat(JobRunServiceImpl.describe(new NullPointerException())).isEqualTo("NullPointerException");
    }

    @Test
    void describe_truncatesTo500() {
        String text = JobRunServiceImpl.describe(new RuntimeException("x".repeat(2000)));

        assertThat(text).hasSize(JobRunServiceImpl.MAX_ERROR_LENGTH).endsWith("…");
    }

    @Test
    void mask_hidesCommonSecretShapes() {
        assertThat(JobRunServiceImpl.mask("jdbc:postgresql://postgres:s3cret@localhost:5432/db"))
                .isEqualTo("jdbc:postgresql://***@localhost:5432/db");
        assertThat(JobRunServiceImpl.mask("Authorization: Bearer abc.def-ghi"))
                .doesNotContain("abc.def-ghi");
        assertThat(JobRunServiceImpl.mask("GET /v1?key=AIzaXYZ&q=1")).isEqualTo("GET /v1?key=***&q=1");
        assertThat(JobRunServiceImpl.mask("refresh_token=1//0abc, client_secret: \"GOCSPX-1\""))
                .isEqualTo("refresh_token=***, client_secret: ***");
        assertThat(JobRunServiceImpl.mask("api-key=k1 token=t1 pwd=p1"))
                .isEqualTo("api-key=*** token=*** pwd=***");
        assertThat(JobRunServiceImpl.mask("NOT_CONFIGURED at UPLOAD: 404 folder not found"))
                .isEqualTo("NOT_CONFIGURED at UPLOAD: 404 folder not found");
    }

    @Test
    void latestByJob_mapsByName() {
        JobRun run = JobRun.builder().jobName("a").trigger(JobTrigger.MANUAL).result(JobRunResult.FAILED)
                .startedAt(START).finishedAt(END).durationMs(2500).error("boom").build();
        when(repository.findLatestPerJob()).thenReturn(List.of(run));

        Map<String, JobRunDto> latest = service.latestByJob();

        JobRunDto dto = latest.get("a");
        assertThat(dto.getJobName()).isEqualTo("a");
        assertThat(dto.getTrigger()).isEqualTo("MANUAL");
        assertThat(dto.getResult()).isEqualTo("FAILED");
        assertThat(dto.getStartedAt()).isEqualTo(START);
        assertThat(dto.getFinishedAt()).isEqualTo(END);
        assertThat(dto.getDurationMs()).isEqualTo(2500);
        assertThat(dto.getError()).isEqualTo("boom");
    }

    @Test
    void recent_clampsLimit() {
        when(repository.findByJobNameOrderByStartedAtDescIdDesc(eq("a"), any(Pageable.class))).thenReturn(List.of());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);

        service.recent("a", 0);
        service.recent("a", 5000);

        verify(repository, times(2)).findByJobNameOrderByStartedAtDescIdDesc(eq("a"), page.capture());
        assertThat(page.getAllValues()).extracting(Pageable::getPageSize)
                .containsExactly(1, JobRunService.KEEP_PER_JOB);
    }
}
