package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.VideoMetadata;
import com.canhlabs.funnyapp.client.YtDlpClient;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportPreviewDto;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoImportServiceImplTest {

    private static final String YT1 = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String YT1_SHORT = "https://youtu.be/dQw4w9WgXcQ";
    private static final String YT2 = "https://www.youtube.com/watch?v=abcdefghijk";

    @Mock VideoImportJobRepository jobRepository;
    @Mock VideoSourceRepository videoSourceRepository;
    @Mock YtDlpClient ytDlpClient;
    @Mock VideoImportWorker worker;

    private AppProperties props;
    private VideoImportServiceImpl service;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getVideoImport().setEnabled(true);
        service = new VideoImportServiceImpl(jobRepository, videoSourceRepository, new ImportUrlValidator(),
                ytDlpClient, worker, props);
        when(jobRepository.save(any(VideoImportJob.class))).thenAnswer(inv -> {
            VideoImportJob j = inv.getArgument(0);
            j.setId(ids.incrementAndGet());
            return j;
        });
    }

    private static CreateVideoImportRequest request(Instant at, String... urls) {
        List<CreateVideoImportRequest.Item> items = new java.util.ArrayList<>();
        for (String u : urls) {
            items.add(new CreateVideoImportRequest.Item(u, null));
        }
        return new CreateVideoImportRequest(items, at);
    }

    private static VideoImportJob job(long id, ImportStatus status) {
        return VideoImportJob.builder().id(id).sourceUrl(YT1).normalizedUrl(YT1).platform(ImportPlatform.YOUTUBE)
                .status(status).scheduledAt(Instant.now()).build();
    }

    private static void assertError(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_whenDisabled_rejectsWithImportDisabled() {
        props.getVideoImport().setEnabled(false);

        assertError(() -> service.create(request(null, YT1), 1L), HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_DISABLED");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void create_runNow_savesJobsNormalizedAndWakesWorker() {
        CreateVideoImportRequest req = new CreateVideoImportRequest(List.of(
                new CreateVideoImportRequest.Item(YT1_SHORT, "  My/Title:  "),
                new CreateVideoImportRequest.Item(YT2, null)), null);

        CreateVideoImportResultDto result = service.create(req, 5L);

        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().get(0).getLine()).isEqualTo(1);
        assertThat(result.getResults().get(0).getJobId()).isEqualTo(101L);
        assertThat(result.getResults().get(1).getJobId()).isEqualTo(102L);
        ArgumentCaptor<VideoImportJob> saved = ArgumentCaptor.forClass(VideoImportJob.class);
        verify(jobRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        VideoImportJob first = saved.getAllValues().get(0);
        assertThat(first.getNormalizedUrl()).isEqualTo(YT1);
        assertThat(first.getSourceUrl()).isEqualTo(YT1_SHORT);
        assertThat(first.getRequestedTitle()).isEqualTo("MyTitle");
        assertThat(first.getPlatform()).isEqualTo(ImportPlatform.YOUTUBE);
        assertThat(first.getStatus()).isEqualTo(ImportStatus.PENDING);
        assertThat(first.getCreatedBy()).isEqualTo(5L);
        assertThat(first.getScheduledAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(saved.getAllValues().get(1).getRequestedTitle()).isNull();
        verify(worker).wakeUp();
    }

    @Test
    void create_perLineErrors_doNotFailTheBatch() {
        when(jobRepository.existsByNormalizedUrlAndStatusIn(eq(YT2), anyCollection())).thenReturn(true);

        CreateVideoImportResultDto result = service.create(request(null, "javascript:alert(1)", YT2, YT1), 1L);

        List<CreateVideoImportResultDto.LineResult> r = result.getResults();
        assertThat(r.get(0)).satisfies(l -> {
            assertThat(l.getLine()).isEqualTo(1);
            assertThat(l.getErrorCode()).isEqualTo("INVALID_URL");
            assertThat(l.getJobId()).isNull();
        });
        assertThat(r.get(1).getErrorCode()).isEqualTo("DUPLICATE_ACTIVE");
        assertThat(r.get(2).getJobId()).isNotNull();
        assertThat(r.get(2).getLine()).isEqualTo(3);
    }

    @Test
    void create_duplicateInsideSameBatch_secondIsDuplicate() {
        org.mockito.Mockito.doAnswer(inv -> {
                    VideoImportJob j = inv.getArgument(0);
                    j.setId(ids.incrementAndGet());
                    return j;
                })
                .doThrow(new DataIntegrityViolationException("uq_video_import_jobs_active_url"))
                .when(jobRepository).save(any(VideoImportJob.class));

        CreateVideoImportResultDto result = service.create(request(null, YT1, YT1_SHORT), 1L);

        assertThat(result.getResults().get(0).getJobId()).isNotNull();
        assertThat(result.getResults().get(1).getErrorCode()).isEqualTo("DUPLICATE_ACTIVE");
    }

    @Test
    void create_nullItemEntry_isInvalidUrl() {
        CreateVideoImportRequest req = new CreateVideoImportRequest(java.util.Arrays.asList(null, new CreateVideoImportRequest.Item(YT1, null)), null);

        CreateVideoImportResultDto result = service.create(req, 1L);

        assertThat(result.getResults().get(0).getErrorCode()).isEqualTo("INVALID_URL");
        assertThat(result.getResults().get(1).getJobId()).isNotNull();
    }

    @Test
    void create_allLinesInvalid_doesNotWakeWorker() {
        service.create(request(null, "nope"), 1L);

        verify(worker, never()).wakeUp();
    }

    @Test
    void create_emptyOrNullItems_is400() {
        assertError(() -> service.create(null, 1L), HttpStatus.BAD_REQUEST, "EMPTY_ITEMS");
        assertError(() -> service.create(new CreateVideoImportRequest(null, null), 1L), HttpStatus.BAD_REQUEST, "EMPTY_ITEMS");
        assertError(() -> service.create(new CreateVideoImportRequest(List.of(), null), 1L), HttpStatus.BAD_REQUEST, "EMPTY_ITEMS");
        verifyNoInteractions(jobRepository);
    }

    @Test
    void create_moreThanBulkMax_is400() {
        props.getVideoImport().setBulkMax(2);

        assertError(() -> service.create(request(null, YT1, YT2, "https://youtu.be/zzzzzzzzzzz"), 1L),
                HttpStatus.BAD_REQUEST, "TOO_MANY_ITEMS");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void create_exactlyBulkMax_isAccepted() {
        props.getVideoImport().setBulkMax(2);

        assertThat(service.create(request(null, YT1, YT2), 1L).getResults()).hasSize(2);
    }

    @Test
    void create_scheduledInFuture_keepsTimeAndDoesNotWake() {
        Instant at = slot(Instant.now().plus(Duration.ofHours(3)));

        CreateVideoImportResultDto result = service.create(request(at, YT1), 1L);

        assertThat(result.getResults().get(0).getJobId()).isNotNull();
        ArgumentCaptor<VideoImportJob> saved = ArgumentCaptor.forClass(VideoImportJob.class);
        verify(jobRepository).save(saved.capture());
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(at);
        verify(worker, never()).wakeUp();
    }

    @Test
    void create_scheduleInPastOrNow_is400() {
        assertError(() -> service.create(request(Instant.now().minusSeconds(60), YT1), 1L),
                HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
        assertError(() -> service.create(request(Instant.now().minusMillis(1), YT1), 1L),
                HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
    }

    @Test
    void create_scheduleBeyond30Days_is400() {
        assertError(() -> service.create(request(Instant.now().plus(Duration.ofDays(31)), YT1), 1L),
                HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void create_scheduleJustInside30Days_isAccepted() {
        Instant at = slot(Instant.now().plus(Duration.ofDays(30)).minusSeconds(600));

        assertThat(service.create(request(at, YT1), 1L).getResults().get(0).getJobId()).isNotNull();
    }

    @Test
    void create_scheduleNotOnFiveMinuteSlot_is400() {
        Instant base = slot(Instant.now().plus(Duration.ofHours(2)));
        assertError(() -> service.create(request(base.plusSeconds(60), YT1), 1L),
                HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
        assertError(() -> service.create(request(base.plusSeconds(30), YT1), 1L),
                HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
        assertThat(service.create(request(base.plusSeconds(300), YT1), 1L).getResults().get(0).getJobId()).isNotNull();
    }

    @Test
    void onSlot_checksFiveMinuteBoundary() {
        assertThat(VideoImportServiceImpl.onSlot(Instant.parse("2026-10-11T02:05:00Z"))).isTrue();
        assertThat(VideoImportServiceImpl.onSlot(Instant.parse("2026-10-11T02:00:00Z"))).isTrue();
        assertThat(VideoImportServiceImpl.onSlot(Instant.parse("2026-10-11T02:07:00Z"))).isFalse();
        assertThat(VideoImportServiceImpl.onSlot(Instant.parse("2026-10-11T02:05:01Z"))).isFalse();
        assertThat(VideoImportServiceImpl.onSlot(Instant.parse("2026-10-11T02:05:00.001Z"))).isFalse();
    }

    /** Rounds down to a 5-minute slot. */
    private static Instant slot(Instant t) {
        long min = t.getEpochSecond() / 60;
        return Instant.ofEpochSecond((min - min % 5) * 60);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_noFilter_newestFirst_andFlagsIngestedWithOneBatchedLookup() {
        VideoImportJob done = job(1, ImportStatus.DONE);
        done.setDriveFileId("drive-1");
        done.setResolvedTitle("Resolved");
        VideoImportJob waiting = job(2, ImportStatus.DONE);
        waiting.setDriveFileId("drive-2");
        VideoImportJob pending = job(3, ImportStatus.PENDING);
        pending.setRequestedTitle("Wins");
        pending.setResolvedTitle("Loses");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(jobRepository.findAll(pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(done, waiting, pending)));
        when(videoSourceRepository.findSourceIdsIn(anyCollection())).thenReturn(List.of("drive-1"));

        Page<VideoImportJobDto> page = service.list(null, PageRequest.of(2, 7));

        assertThat(page.getContent()).extracting(VideoImportJobDto::isIngested).containsExactly(true, false, false);
        assertThat(page.getContent()).extracting(VideoImportJobDto::getTitle).containsExactly("Resolved", null, "Wins");
        assertThat(page.getContent()).extracting(VideoImportJobDto::getPhase).containsExactly("DONE", "DONE", "QUEUED");
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        verify(videoSourceRepository).findSourceIdsIn(anyCollection());
    }

    @Test
    void list_withStatus_usesStatusQuery_andSkipsLookupWithoutDriveIds() {
        when(jobRepository.findByStatus(eq(ImportStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job(1, ImportStatus.FAILED))));

        Page<VideoImportJobDto> page = service.list(ImportStatus.FAILED, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getPhase()).isNull();
        verifyNoInteractions(videoSourceRepository);
    }

    // ── preview ───────────────────────────────────────────────────────────────

    @Test
    void preview_returnsSanitizedTitleAndPlatform() {
        when(ytDlpClient.fetchMetadata(YT1)).thenReturn(new VideoMetadata("dQw4w9WgXcQ", "Hello: World?", 212L));

        ImportPreviewDto dto = service.preview(YT1_SHORT);

        assertThat(dto.getTitle()).isEqualTo("Hello World");
        assertThat(dto.getPlatform()).isEqualTo(ImportPlatform.YOUTUBE);
        assertThat(dto.getDurationSec()).isEqualTo(212L);
    }

    @Test
    void preview_blankTitle_isNull() {
        when(ytDlpClient.fetchMetadata(anyString())).thenReturn(new VideoMetadata("x", null, null));

        assertThat(service.preview(YT1).getTitle()).isNull();
    }

    @Test
    void preview_invalidUrl_is400_andNeverRunsYtDlp() {
        assertError(() -> service.preview("http://evil.com"), HttpStatus.BAD_REQUEST, "INVALID_URL");
        assertError(() -> service.preview(null), HttpStatus.BAD_REQUEST, "INVALID_URL");
        verifyNoInteractions(ytDlpClient);
    }

    @Test
    void preview_ytDlpFailures_mapToHttpStatusWithCodeOnly() {
        when(ytDlpClient.fetchMetadata(anyString()))
                .thenThrow(new ImportException(ImportErrorCode.NOT_INSTALLED))
                .thenThrow(new ImportException(ImportErrorCode.TIMEOUT))
                .thenThrow(new ImportException(ImportErrorCode.LOGIN_REQUIRED))
                .thenThrow(new ImportException(ImportErrorCode.EXTRACTOR_ERROR));

        assertError(() -> service.preview(YT1), HttpStatus.SERVICE_UNAVAILABLE, "NOT_INSTALLED");
        assertError(() -> service.preview(YT1), HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT");
        assertError(() -> service.preview(YT1), HttpStatus.UNPROCESSABLE_ENTITY, "LOGIN_REQUIRED");
        assertError(() -> service.preview(YT1), HttpStatus.BAD_GATEWAY, "EXTRACTOR_ERROR");
    }

    // ── run-now / cancel / retry ──────────────────────────────────────────────

    @Test
    void runNow_pending_updatesAndWakesWorker() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.PENDING)));
        when(jobRepository.runNowIfPending(5L)).thenReturn(1);

        VideoImportJobDto dto = service.runNow(5L);

        assertThat(dto.getId()).isEqualTo(5L);
        verify(worker).wakeUp();
    }

    @Test
    void runNow_notPending_is409_andNoUpdate() {
        for (ImportStatus s : List.of(ImportStatus.DOWNLOADING, ImportStatus.UPLOADING, ImportStatus.DONE,
                ImportStatus.FAILED, ImportStatus.CANCELLED)) {
            when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, s)));
            assertError(() -> service.runNow(5L), HttpStatus.CONFLICT, "INVALID_STATE");
        }
        verify(jobRepository, never()).runNowIfPending(any());
        verify(worker, never()).wakeUp();
    }

    @Test
    void runNow_lostRace_is409() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.PENDING)));
        when(jobRepository.runNowIfPending(5L)).thenReturn(0);

        assertError(() -> service.runNow(5L), HttpStatus.CONFLICT, "INVALID_STATE");
    }

    @Test
    void unknownJob_is404() {
        when(jobRepository.findById(9L)).thenReturn(Optional.empty());

        assertError(() -> service.runNow(9L), HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND");
        assertError(() -> service.cancel(9L), HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND");
        assertError(() -> service.retry(9L), HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND");
    }

    @Test
    void cancel_pending_cancelsInDb() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.PENDING)));
        when(jobRepository.cancelIfPending(5L)).thenReturn(1);

        service.cancel(5L);

        verify(jobRepository).cancelIfPending(5L);
        verify(worker, never()).requestCancel(any());
    }

    @Test
    void cancel_pendingThatWasClaimedMeanwhile_fallsBackToRunningCancel() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.PENDING)));
        when(jobRepository.cancelIfPending(5L)).thenReturn(0);
        when(worker.requestCancel(5L)).thenReturn(true);

        service.cancel(5L);

        verify(worker).requestCancel(5L);
    }

    @Test
    void cancel_running_signalsWorker() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.DOWNLOADING)));
        when(worker.requestCancel(5L)).thenReturn(true);

        VideoImportJobDto dto = service.cancel(5L);

        assertThat(dto.getPhase()).isEqualTo("DOWNLOAD");
        verify(worker).requestCancel(5L);
        verify(jobRepository, never()).cancelIfPending(any());
    }

    @Test
    void cancel_runningButNotOnThisNode_is409() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.UPLOADING)));
        when(worker.requestCancel(5L)).thenReturn(false);

        assertError(() -> service.cancel(5L), HttpStatus.CONFLICT, "INVALID_STATE");
    }

    @Test
    void cancel_finalStates_are409() {
        for (ImportStatus s : List.of(ImportStatus.DONE, ImportStatus.FAILED, ImportStatus.CANCELLED)) {
            when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, s)));
            assertError(() -> service.cancel(5L), HttpStatus.CONFLICT, "INVALID_STATE");
        }
    }

    @Test
    void retry_failedOrCancelled_requeuesAndWakes() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.FAILED)));
        when(jobRepository.retryIfFinal(5L)).thenReturn(1);

        service.retry(5L);

        verify(jobRepository).retryIfFinal(5L);
        verify(worker).wakeUp();
    }

    @Test
    void retry_wrongState_is409() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.DONE)));
        when(jobRepository.retryIfFinal(5L)).thenReturn(0);

        assertError(() -> service.retry(5L), HttpStatus.CONFLICT, "INVALID_STATE");
        verify(worker, never()).wakeUp();
    }

    @Test
    void retry_sameUrlAlreadyActive_is409Duplicate() {
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job(5, ImportStatus.FAILED)));
        when(jobRepository.retryIfFinal(5L)).thenThrow(new DataIntegrityViolationException("dup"));

        assertError(() -> service.retry(5L), HttpStatus.CONFLICT, "DUPLICATE_ACTIVE");
    }

    @Test
    void toDto_mapsAllContractFields() {
        Instant now = Instant.now();
        VideoImportJob j = job(8, ImportStatus.UPLOADING);
        j.setProgressPct(40);
        j.setDownloadedBytes(10);
        j.setTotalBytes(25);
        j.setStartedAt(now);
        j.setFinishedAt(now);
        j.setErrorCode("X");
        j.setErrorMessage("msg");
        j.setAttempts(2);
        j.setCreatedAt(now);

        VideoImportJobDto dto = VideoImportServiceImpl.toDto(j, true);

        assertThat(dto.getPhase()).isEqualTo("UPLOAD");
        assertThat(dto.getProgressPct()).isEqualTo(40);
        assertThat(dto.getDownloadedBytes()).isEqualTo(10);
        assertThat(dto.getTotalBytes()).isEqualTo(25);
        assertThat(dto.getStartedAt()).isEqualTo(now);
        assertThat(dto.getFinishedAt()).isEqualTo(now);
        assertThat(dto.getErrorCode()).isEqualTo("X");
        assertThat(dto.getErrorMessage()).isEqualTo("msg");
        assertThat(dto.getAttempts()).isEqualTo(2);
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.isIngested()).isTrue();
        assertThat(dto.getSourceUrl()).isEqualTo(YT1);
    }
}
