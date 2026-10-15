package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportRequest;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoSuggestion;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.enums.SuggestionFilter;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.event.SuggestionReviewedEvent;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.SuggestionLiveRef;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoImportJobRepository;
import com.canhlabs.funnyapp.repo.VideoSuggestionRepository;
import com.canhlabs.funnyapp.service.VideoImportService;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import com.canhlabs.funnyapp.utils.ImportUrlResolver;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import com.canhlabs.funnyapp.utils.NotificationText;
import com.canhlabs.funnyapp.utils.TitleSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
public class VideoSuggestionServiceImpl implements VideoSuggestionService {

    static final int MAX_PAGE_SIZE = 50;
    static final Duration LIMIT_WINDOW = Duration.ofHours(24);
    /** An import job in one of these states already covers the URL. */
    static final List<ImportStatus> BLOCKING_IMPORT_STATUSES = blockingStatuses();

    static final String KEY_INVALID_URL = "error.suggestion.invalidUrl";
    static final String KEY_DUPLICATE = "error.suggestion.duplicate";
    static final String KEY_DAILY_LIMIT = "error.suggestion.dailyLimit";
    static final String KEY_NOT_FOUND = "error.suggestion.notFound";
    static final String KEY_NOT_PENDING = "error.suggestion.notPending";
    static final String KEY_NOTE_REQUIRED = "error.suggestion.noteRequired";
    static final String KEY_IMPORT_DISABLED = "error.import.disabled";
    static final String KEY_IMPORT_DUPLICATE = "error.import.duplicateActive";
    static final String KEY_IMPORT_INVALID_URL = "error.import.invalidUrl";
    static final String KEY_IMPORT_FAILED = "error.import.failed";

    private final VideoSuggestionRepository repository;
    private final VideoImportJobRepository jobRepository;
    private final VideoImportService importService;
    private final VideoImportWorker importWorker;
    private final ImportUrlResolver urlResolver;
    private final UserRepo userRepo;
    private final AppProperties appProps;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public VideoSuggestionServiceImpl(VideoSuggestionRepository repository,
                                      VideoImportJobRepository jobRepository,
                                      VideoImportService importService,
                                      VideoImportWorker importWorker,
                                      ImportUrlResolver urlResolver,
                                      UserRepo userRepo,
                                      AppProperties appProps,
                                      ApplicationEventPublisher eventPublisher,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.importService = importService;
        this.importWorker = importWorker;
        this.urlResolver = urlResolver;
        this.userRepo = userRepo;
        this.appProps = appProps;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private static List<ImportStatus> blockingStatuses() {
        List<ImportStatus> all = new ArrayList<>(ImportStatus.ACTIVE);
        all.add(ImportStatus.DONE);
        return List.copyOf(all);
    }

    // ── user ────────────────────────────────────────────────────────────────────

    @Override
    public SuggestionDto submit(Long userId, String url, String title, String note) {
        ImportUrlValidator.ImportUrl parsed = urlResolver.resolve(url)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, 4301, KEY_INVALID_URL));

        int limit = appProps.getVideoSuggestion().getDailyLimit();
        if (repository.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(LIMIT_WINDOW)) >= limit) {
            throw CustomException.of(HttpStatus.TOO_MANY_REQUESTS, 4291, KEY_DAILY_LIMIT, limit);
        }
        if (repository.existsByNormalizedUrlAndStatus(parsed.normalizedUrl(), SuggestionStatus.PENDING)
                || jobRepository.existsByNormalizedUrlAndStatusIn(parsed.normalizedUrl(), BLOCKING_IMPORT_STATUSES)) {
            throw duplicate();
        }
        VideoSuggestion suggestion = VideoSuggestion.builder()
                .userId(userId)
                .sourceUrl(parsed.sourceUrl())
                .normalizedUrl(parsed.normalizedUrl())
                .platform(parsed.platform())
                .title(blankToNull(TitleSanitizer.sanitize(title)))
                .note(blankToNull(note == null ? null : note.trim()))
                .build();
        try {
            return toDto(repository.saveAndFlush(suggestion), null, null, false);
        } catch (DataIntegrityViolationException e) {
            // lost the race against the partial unique index on pending normalized_url
            throw duplicate();
        }
    }

    @Override
    public Page<SuggestionDto> mine(Long userId, SuggestionFilter filter, int page, int size) {
        Pageable newestFirst = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<VideoSuggestion> result;
        if (filter == null) {
            result = repository.findByUserId(userId, newestFirst);
        } else if (filter == SuggestionFilter.LIVE) {
            result = repository.findLiveByUserId(userId, newestFirst);
        } else {
            result = repository.findByUserIdAndStatus(userId, SuggestionStatus.valueOf(filter.name()), newestFirst);
        }
        Map<Long, Long> live = liveVideoIds(result.getContent());
        return result.map(s -> toDto(s, live.get(s.getId()), null, false));
    }

    // ── admin ───────────────────────────────────────────────────────────────────

    @Override
    public Page<SuggestionDto> list(SuggestionFilter filter, int page, int size) {
        Pageable unsorted = PageRequest.of(Math.max(page, 0), clampSize(size)); // order is part of the queries
        Page<VideoSuggestion> result;
        if (filter == null) {
            result = repository.findAllPendingFirst(unsorted);
        } else if (filter == SuggestionFilter.LIVE) {
            result = repository.findAllLiveNewest(unsorted);
        } else {
            result = repository.findAllByStatusNewest(SuggestionStatus.valueOf(filter.name()), unsorted);
        }
        Map<Long, Long> live = liveVideoIds(result.getContent());
        Map<Long, String> names = suggesterNames(result.getContent());
        return result.map(s -> toDto(s, live.get(s.getId()), names.get(s.getUserId()), true));
    }

    @Override
    public long pendingCount() {
        return repository.countByStatus(SuggestionStatus.PENDING);
    }

    @Override
    public SuggestionDto approve(Long id, String titleOverride, Long adminId) {
        if (!appProps.getVideoImport().isEnabled()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, 4100, KEY_IMPORT_DISABLED);
        }
        SuggestionDto dto = inTx(() -> {
            VideoSuggestion s = lockPending(id);
            String override = blankToNull(TitleSanitizer.sanitize(titleOverride));
            if (override != null) {
                s.setTitle(override);
            }
            s.setImportJobId(createImportJob(s, adminId));
            s.setStatus(SuggestionStatus.APPROVED);
            markReviewed(s, adminId);
            VideoSuggestion saved = repository.save(s);
            eventPublisher.publishEvent(new SuggestionReviewedEvent(
                    saved.getId(), saved.getUserId(), true, snippetOf(saved), null));
            return toDto(saved, null, suggesterNames(List.of(saved)).get(saved.getUserId()), true);
        });
        // the import created the job inside the transaction; wake the worker again now that it is visible
        importWorker.wakeUp();
        return dto;
    }

    @Override
    public SuggestionDto reject(Long id, SuggestionRejectReason reason, String note, Long adminId) {
        String cleanNote = blankToNull(note == null ? null : note.trim());
        if (reason == SuggestionRejectReason.OTHER && cleanNote == null) {
            throw error(HttpStatus.BAD_REQUEST, 4001, KEY_NOTE_REQUIRED);
        }
        return inTx(() -> {
            VideoSuggestion s = lockPending(id);
            s.setStatus(SuggestionStatus.REJECTED);
            s.setRejectReason(reason);
            s.setRejectNote(cleanNote);
            markReviewed(s, adminId);
            VideoSuggestion saved = repository.save(s);
            eventPublisher.publishEvent(new SuggestionReviewedEvent(
                    saved.getId(), saved.getUserId(), false, snippetOf(saved), reason == null ? null : reason.name()));
            return toDto(saved, null, suggesterNames(List.of(saved)).get(saved.getUserId()), true);
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private VideoSuggestion lockPending(Long id) {
        VideoSuggestion s = repository.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4042, KEY_NOT_FOUND));
        if (s.getStatus() != SuggestionStatus.PENDING) {
            throw error(HttpStatus.CONFLICT, 4093, KEY_NOT_PENDING);
        }
        return s;
    }

    private static void markReviewed(VideoSuggestion s, Long adminId) {
        s.setReviewedBy(adminId);
        s.setReviewedAt(Instant.now());
    }

    /**
     * Exactly one job, scheduled now; a rejected import line (e.g. a racing active job) becomes a 409.
     * Passes the normalized URL: a share link was already expanded at submit, so no second redirect probe.
     */
    private Long createImportJob(VideoSuggestion s, Long adminId) {
        CreateVideoImportRequest request = new CreateVideoImportRequest(
                List.of(new CreateVideoImportRequest.Item(s.getNormalizedUrl(), s.getTitle())), null);
        CreateVideoImportResultDto.LineResult line = importService.create(request, adminId).getResults().get(0);
        if (line.getJobId() == null) {
            throw error(HttpStatus.CONFLICT, 4094, importErrorKey(line.getErrorCode()));
        }
        return line.getJobId();
    }

    private static String importErrorKey(String code) {
        if (VideoImportServiceImpl.DUPLICATE_ACTIVE.equals(code)) {
            return KEY_IMPORT_DUPLICATE;
        }
        if (VideoImportServiceImpl.INVALID_URL.equals(code)) {
            return KEY_IMPORT_INVALID_URL;
        }
        return KEY_IMPORT_FAILED;
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** video_sources id per suggestion id, for the suggestions of this page, in one query. */
    private Map<Long, Long> liveVideoIds(Collection<VideoSuggestion> suggestions) {
        Set<Long> ids = new HashSet<>();
        for (VideoSuggestion s : suggestions) {
            if (s.getStatus() == SuggestionStatus.APPROVED && s.getImportJobId() != null) {
                ids.add(s.getId());
            }
        }
        Map<Long, Long> byId = new HashMap<>();
        if (ids.isEmpty()) {
            return byId;
        }
        for (SuggestionLiveRef ref : repository.findLiveRefs(ids)) {
            byId.putIfAbsent(ref.suggestionId(), ref.videoSourceId());
        }
        return byId;
    }

    private Map<Long, String> suggesterNames(Collection<VideoSuggestion> suggestions) {
        Set<Long> userIds = new HashSet<>();
        suggestions.forEach(s -> userIds.add(s.getUserId()));
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User u : userRepo.findAllById(userIds)) {
            names.put(u.getId(), NotificationText.actorDisplay(u.getUserName()));
        }
        return names;
    }

    private static SuggestionDto toDto(VideoSuggestion s, Long liveVideoId, String suggesterName, boolean admin) {
        return SuggestionDto.builder()
                .id(s.getId())
                .sourceUrl(s.getSourceUrl())
                .platform(s.getPlatform())
                .title(s.getTitle())
                .note(s.getNote())
                .status(s.getStatus())
                .live(liveVideoId != null)
                .liveVideoId(liveVideoId)
                .rejectReason(s.getRejectReason())
                .rejectNote(s.getRejectNote())
                .createdAt(s.getCreatedAt())
                .reviewedAt(s.getReviewedAt())
                .suggesterName(admin ? suggesterName : null)
                .importJobId(admin ? s.getImportJobId() : null)
                .build();
    }

    /** Title, or the source URL when the suggester gave none. */
    public static String snippetOf(VideoSuggestion s) {
        String text = s.getTitle() == null || s.getTitle().isBlank() ? s.getSourceUrl() : s.getTitle();
        return NotificationText.snippet(text);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static CustomException duplicate() {
        return error(HttpStatus.CONFLICT, 4092, KEY_DUPLICATE);
    }

    private static CustomException error(HttpStatus status, int subCode, String key) {
        return CustomException.of(status, subCode, key);
    }
}
