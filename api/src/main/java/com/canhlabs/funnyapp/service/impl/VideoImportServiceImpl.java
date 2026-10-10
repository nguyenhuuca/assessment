package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.VideoMetadata;
import com.canhlabs.funnyapp.client.YtDlpClient;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportPreviewDto;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.entity.VideoImportJob;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoImportService;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import com.canhlabs.funnyapp.utils.TitleSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoImportServiceImpl implements VideoImportService {

    static final String INVALID_URL = "INVALID_URL";
    static final String DUPLICATE_ACTIVE = "DUPLICATE_ACTIVE";
    static final String INVALID_STATE = "INVALID_STATE";
    static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(30);
    /** The worker ticks every 5 minutes on the clock, so schedules must sit on a 5-minute slot. */
    static final int SCHEDULE_SLOT_MINUTES = 5;

    private final VideoImportJobRepository jobRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final ImportUrlValidator urlValidator;
    private final YtDlpClient ytDlpClient;
    private final VideoImportWorker worker;
    private final AppProperties appProps;

    @Override
    public CreateVideoImportResultDto create(CreateVideoImportRequest request, Long adminId) {
        if (!appProps.getVideoImport().isEnabled()) {
            // worker is a no-op when disabled: refuse instead of queueing jobs that never run
            throw error(HttpStatus.SERVICE_UNAVAILABLE, 4100, "IMPORT_DISABLED");
        }
        List<CreateVideoImportRequest.Item> items = request == null ? null : request.getItems();
        if (items == null || items.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, 4101, "EMPTY_ITEMS");
        }
        int bulkMax = appProps.getVideoImport().getBulkMax();
        if (items.size() > bulkMax) {
            throw error(HttpStatus.BAD_REQUEST, 4102, "TOO_MANY_ITEMS");
        }
        Instant now = Instant.now();
        Instant scheduledAt = resolveSchedule(request.getScheduledAt(), now);

        List<CreateVideoImportResultDto.LineResult> results = new ArrayList<>(items.size());
        boolean runsNow = request.getScheduledAt() == null;
        boolean created = false;
        for (int i = 0; i < items.size(); i++) {
            CreateVideoImportResultDto.LineResult result = createOne(i + 1, items.get(i), scheduledAt, adminId);
            created |= result.getJobId() != null;
            results.add(result);
        }
        if (created && runsNow) {
            worker.wakeUp();
        }
        return CreateVideoImportResultDto.builder().results(results).build();
    }

    private CreateVideoImportResultDto.LineResult createOne(int line, CreateVideoImportRequest.Item item,
                                                            Instant scheduledAt, Long adminId) {
        Optional<ImportUrlValidator.ImportUrl> parsed = item == null ? Optional.empty() : urlValidator.parse(item.getUrl());
        if (parsed.isEmpty()) {
            return lineError(line, INVALID_URL);
        }
        ImportUrlValidator.ImportUrl url = parsed.get();
        if (jobRepository.existsByNormalizedUrlAndStatusIn(url.normalizedUrl(), ImportStatus.ACTIVE)) {
            return lineError(line, DUPLICATE_ACTIVE);
        }
        String title = TitleSanitizer.sanitize(item.getTitle());
        VideoImportJob job = VideoImportJob.builder()
                .sourceUrl(url.sourceUrl())
                .normalizedUrl(url.normalizedUrl())
                .platform(url.platform())
                .requestedTitle(title.isEmpty() ? null : title)
                .scheduledAt(scheduledAt)
                .createdBy(adminId)
                .build();
        try {
            VideoImportJob saved = jobRepository.save(job);
            return CreateVideoImportResultDto.LineResult.builder().line(line).jobId(saved.getId()).build();
        } catch (DataIntegrityViolationException e) {
            // lost the race against the partial unique index
            return lineError(line, DUPLICATE_ACTIVE);
        }
    }

    /** Whole minute divisible by 5 (UTC and Asia/Ho_Chi_Minh agree: the offset is whole hours). */
    static boolean onSlot(Instant t) {
        long epochSeconds = t.getEpochSecond();
        return t.getNano() == 0 && epochSeconds % 60 == 0 && (epochSeconds / 60) % SCHEDULE_SLOT_MINUTES == 0;
    }

    private static CreateVideoImportResultDto.LineResult lineError(int line, String code) {
        return CreateVideoImportResultDto.LineResult.builder().line(line).errorCode(code).build();
    }

    private Instant resolveSchedule(Instant requested, Instant now) {
        if (requested == null) {
            return now;
        }
        if (!requested.isAfter(now) || requested.isAfter(now.plus(MAX_SCHEDULE_AHEAD)) || !onSlot(requested)) {
            throw error(HttpStatus.BAD_REQUEST, 4103, "INVALID_SCHEDULE");
        }
        return requested;
    }

    @Override
    public Page<VideoImportJobDto> list(ImportStatus status, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<VideoImportJob> page = status == null
                ? jobRepository.findAll(newestFirst)
                : jobRepository.findByStatus(status, newestFirst);
        Set<String> ingested = ingestedIds(page.getContent());
        return page.map(j -> toDto(j, j.getDriveFileId() != null && ingested.contains(j.getDriveFileId())));
    }

    private Set<String> ingestedIds(List<VideoImportJob> jobs) {
        Set<String> ids = jobs.stream()
                .map(VideoImportJob::getDriveFileId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(videoSourceRepository.findSourceIdsIn(ids));
    }

    @Override
    public ImportPreviewDto preview(String url) {
        ImportUrlValidator.ImportUrl parsed = urlValidator.parse(url)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, 4001, INVALID_URL));
        try {
            VideoMetadata meta = ytDlpClient.fetchMetadata(parsed.normalizedUrl());
            String title = TitleSanitizer.sanitize(meta.title());
            return ImportPreviewDto.builder()
                    .title(title.isEmpty() ? null : title)
                    .platform(parsed.platform())
                    .durationSec(meta.durationSec())
                    .build();
        } catch (ImportException e) {
            throw error(previewStatus(e.getErrorCode()), 4200, e.getErrorCode());
        }
    }

    private static HttpStatus previewStatus(String code) {
        return switch (code) {
            case ImportErrorCode.NOT_INSTALLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case ImportErrorCode.TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case ImportErrorCode.UNSUPPORTED_URL, ImportErrorCode.LOGIN_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    @Override
    public VideoImportJobDto runNow(Long id) {
        VideoImportJob existing = load(id);
        if (existing.getStatus() != ImportStatus.PENDING || jobRepository.runNowIfPending(id) == 0) {
            throw error(HttpStatus.CONFLICT, 4091, INVALID_STATE);
        }
        worker.wakeUp();
        return toDto(load(id), false);
    }

    @Override
    public VideoImportJobDto cancel(Long id) {
        VideoImportJob existing = load(id);
        ImportStatus status = existing.getStatus();
        boolean accepted = switch (status) {
            case PENDING -> jobRepository.cancelIfPending(id) == 1 || worker.requestCancel(id);
            case DOWNLOADING, UPLOADING -> worker.requestCancel(id);
            default -> false;
        };
        if (!accepted) {
            throw error(HttpStatus.CONFLICT, 4091, INVALID_STATE);
        }
        return toDto(load(id), false);
    }

    @Override
    public VideoImportJobDto retry(Long id) {
        load(id);
        int updated;
        try {
            updated = jobRepository.retryIfFinal(id);
        } catch (DataIntegrityViolationException e) {
            throw error(HttpStatus.CONFLICT, 4092, DUPLICATE_ACTIVE);
        }
        if (updated == 0) {
            throw error(HttpStatus.CONFLICT, 4091, INVALID_STATE);
        }
        worker.wakeUp();
        return toDto(load(id), false);
    }

    private VideoImportJob load(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4041, "IMPORT_NOT_FOUND"));
    }

    static VideoImportJobDto toDto(VideoImportJob j, boolean ingested) {
        String title = j.getRequestedTitle() != null ? j.getRequestedTitle() : j.getResolvedTitle();
        return VideoImportJobDto.builder()
                .id(j.getId())
                .sourceUrl(j.getSourceUrl())
                .platform(j.getPlatform())
                .title(title)
                .status(j.getStatus())
                .phase(phase(j.getStatus()))
                .progressPct(j.getProgressPct())
                .downloadedBytes(j.getDownloadedBytes())
                .totalBytes(j.getTotalBytes())
                .scheduledAt(j.getScheduledAt())
                .startedAt(j.getStartedAt())
                .finishedAt(j.getFinishedAt())
                .driveFileId(j.getDriveFileId())
                .ingested(ingested)
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .attempts(j.getAttempts())
                .createdAt(j.getCreatedAt())
                .build();
    }

    private static String phase(ImportStatus status) {
        return switch (status) {
            case PENDING -> "QUEUED";
            case DOWNLOADING -> "DOWNLOAD";
            case UPLOADING -> "UPLOAD";
            case DONE -> "DONE";
            default -> null;
        };
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }
}
