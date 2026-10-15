package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.RedirectProbe;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoSuggestion;
import com.canhlabs.funnyapp.enums.ImportPlatform;
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
import com.canhlabs.funnyapp.utils.ImportUrlResolver;
import com.canhlabs.funnyapp.utils.ImportUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoSuggestionServiceImplTest {

    private static final String YT = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final long USER = 7L;
    private static final long ADMIN = 99L;

    @Mock VideoSuggestionRepository repository;
    @Mock VideoImportJobRepository jobRepository;
    @Mock VideoImportService importService;
    @Mock VideoImportWorker importWorker;
    @Mock UserRepo userRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PlatformTransactionManager transactionManager;

    private AppProperties props;
    private VideoSuggestionServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getVideoImport().setEnabled(true);
        service = new VideoSuggestionServiceImpl(repository, jobRepository, importService, importWorker,
                new ImportUrlResolver(new ImportUrlValidator(), url -> Optional.empty()), userRepo, props, eventPublisher, transactionManager);
        when(repository.saveAndFlush(any(VideoSuggestion.class))).thenAnswer(inv -> {
            VideoSuggestion s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(repository.save(any(VideoSuggestion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private VideoSuggestionServiceImpl serviceWithProbe(RedirectProbe probe) {
        return new VideoSuggestionServiceImpl(repository, jobRepository, importService, importWorker,
                new ImportUrlResolver(new ImportUrlValidator(), probe), userRepo, props, eventPublisher, transactionManager);
    }

    private static VideoSuggestion pending(long id) {
        return VideoSuggestion.builder().id(id).userId(USER).sourceUrl(YT).normalizedUrl(YT)
                .platform(ImportPlatform.YOUTUBE).title("Funny cat").build();
    }

    private static void assertError(Runnable call, HttpStatus status, String key) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessageKey()).isEqualTo(key);
        });
    }

    private static User user(long id, String email) {
        User u = new User();
        u.setId(id);
        u.setUserName(email);
        return u;
    }

    private void importCreates(long jobId) {
        when(importService.createResolved(any(), any(), eq(ADMIN)))
                .thenReturn(CreateVideoImportResultDto.LineResult.builder().line(1).jobId(jobId).build());
    }

    // ── submit ──────────────────────────────────────────────────────────────────

    @Test
    void submit_validUrl_savesPendingNormalized_withoutAdminFields() {
        SuggestionDto dto = service.submit(USER, "  https://youtu.be/dQw4w9WgXcQ ", "  Funny   cat ", " why fun ");

        ArgumentCaptor<VideoSuggestion> captor = ArgumentCaptor.forClass(VideoSuggestion.class);
        verify(repository).saveAndFlush(captor.capture());
        VideoSuggestion saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getNormalizedUrl()).isEqualTo(YT);
        assertThat(saved.getPlatform()).isEqualTo(ImportPlatform.YOUTUBE);
        assertThat(saved.getTitle()).isEqualTo("Funny cat");
        assertThat(saved.getNote()).isEqualTo("why fun");
        assertThat(saved.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(dto.getId()).isEqualTo(55L);
        assertThat(dto.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(dto.isLive()).isFalse();
        assertThat(dto.getLiveVideoId()).isNull();
        assertThat(dto.getSuggesterName()).isNull();
        assertThat(dto.getImportJobId()).isNull();
    }

    @Test
    void submit_blankTitleAndNote_storedAsNull() {
        service.submit(USER, YT, "   ", "  ");

        ArgumentCaptor<VideoSuggestion> captor = ArgumentCaptor.forClass(VideoSuggestion.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTitle()).isNull();
        assertThat(captor.getValue().getNote()).isNull();
    }

    @Test
    void submit_nullTitleAndNote_storedAsNull() {
        service.submit(USER, YT, null, null);

        ArgumentCaptor<VideoSuggestion> captor = ArgumentCaptor.forClass(VideoSuggestion.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTitle()).isNull();
        assertThat(captor.getValue().getNote()).isNull();
    }

    @Test
    void submit_invalidUrl_400() {
        assertError(() -> service.submit(USER, "http://www.youtube.com/watch?v=dQw4w9WgXcQ", null, null),
                HttpStatus.BAD_REQUEST, "error.suggestion.invalidUrl");
        assertError(() -> service.submit(USER, "https://evil.example.com/x", null, null),
                HttpStatus.BAD_REQUEST, "error.suggestion.invalidUrl");
        assertError(() -> service.submit(USER, null, null, null),
                HttpStatus.BAD_REQUEST, "error.suggestion.invalidUrl");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void submit_duplicatePending_409() {
        when(repository.existsByNormalizedUrlAndStatus(YT, SuggestionStatus.PENDING)).thenReturn(true);

        assertError(() -> service.submit(USER, YT, null, null), HttpStatus.CONFLICT, "error.suggestion.duplicate");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void submit_activeOrDoneImportForUrl_409() {
        when(jobRepository.existsByNormalizedUrlAndStatusIn(eq(YT), anyCollection())).thenReturn(true);

        assertError(() -> service.submit(USER, YT, null, null), HttpStatus.CONFLICT, "error.suggestion.duplicate");

        ArgumentCaptor<java.util.Collection<ImportStatus>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(jobRepository).existsByNormalizedUrlAndStatusIn(eq(YT), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                ImportStatus.PENDING, ImportStatus.DOWNLOADING, ImportStatus.UPLOADING, ImportStatus.DONE);
    }

    @Test
    void submit_lostRaceOnUniqueIndex_409() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertError(() -> service.submit(USER, YT, null, null), HttpStatus.CONFLICT, "error.suggestion.duplicate");
    }

    @Test
    void submit_sixthInTwentyFourHours_429() {
        when(repository.countByUserIdAndCreatedAtAfter(eq(USER), any(Instant.class))).thenReturn(5L);

        assertThatThrownBy(() -> service.submit(USER, YT, null, null)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getMessageKey()).isEqualTo("error.suggestion.dailyLimit");
            assertThat(e.getArgs()).containsExactly(5);
        });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void submit_fifthInTwentyFourHours_allowed_andWindowIsRolling24h() {
        when(repository.countByUserIdAndCreatedAtAfter(eq(USER), any(Instant.class))).thenReturn(4L);
        Instant before = Instant.now();

        service.submit(USER, YT, null, null);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countByUserIdAndCreatedAtAfter(eq(USER), since.capture());
        assertThat(since.getValue()).isBetween(before.minusSeconds(24 * 3600 + 5), Instant.now().minusSeconds(24 * 3600 - 5));
    }

    @Test
    void submit_limitIsConfigurable() {
        props.getVideoSuggestion().setDailyLimit(2);
        when(repository.countByUserIdAndCreatedAtAfter(eq(USER), any(Instant.class))).thenReturn(2L);

        assertError(() -> service.submit(USER, YT, null, null), HttpStatus.TOO_MANY_REQUESTS, "error.suggestion.dailyLimit");
    }

    // ── mine ────────────────────────────────────────────────────────────────────

    private static VideoSuggestion approved(long id, Long jobId) {
        VideoSuggestion s = pending(id);
        s.setStatus(SuggestionStatus.APPROVED);
        s.setImportJobId(jobId);
        return s;
    }

    @Test
    void mine_noFilter_newestFirst_andLiveVideoIdFromOneBatchQuery() {
        VideoSuggestion live = approved(1, 10L);
        VideoSuggestion notYet = approved(2, 11L);
        VideoSuggestion waiting = pending(3);
        when(repository.findByUserId(eq(USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(live, notYet, waiting)));
        when(repository.findLiveRefs(anyCollection())).thenReturn(List.of(new SuggestionLiveRef(1L, 501L)));

        Page<SuggestionDto> page = service.mine(USER, null, 0, 20);

        assertThat(page.getContent()).extracting(SuggestionDto::getId).containsExactly(1L, 2L, 3L);
        assertThat(page.getContent().get(0).isLive()).isTrue();
        assertThat(page.getContent().get(0).getLiveVideoId()).isEqualTo(501L);
        assertThat(page.getContent().get(1).isLive()).isFalse();
        assertThat(page.getContent().get(1).getLiveVideoId()).isNull();
        assertThat(page.getContent().get(2).isLive()).isFalse();
        // user endpoints never expose admin fields
        assertThat(page.getContent().get(0).getImportJobId()).isNull();
        assertThat(page.getContent().get(0).getSuggesterName()).isNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserId(eq(USER), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        ArgumentCaptor<java.util.Collection<Long>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(repository, times(1)).findLiveRefs(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 2L); // only approved suggestions with a job
    }

    @Test
    void mine_statusFilters_routeToTheRightQuery() {
        when(repository.findByUserIdAndStatus(eq(USER), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(repository.findLiveByUserId(eq(USER), any(Pageable.class))).thenReturn(Page.empty());

        service.mine(USER, SuggestionFilter.REJECTED, 0, 20);
        verify(repository).findByUserIdAndStatus(eq(USER), eq(SuggestionStatus.REJECTED), any(Pageable.class));

        service.mine(USER, SuggestionFilter.PENDING, 0, 20);
        verify(repository).findByUserIdAndStatus(eq(USER), eq(SuggestionStatus.PENDING), any(Pageable.class));

        service.mine(USER, SuggestionFilter.APPROVED, 0, 20);
        verify(repository).findByUserIdAndStatus(eq(USER), eq(SuggestionStatus.APPROVED), any(Pageable.class));

        service.mine(USER, SuggestionFilter.LIVE, 0, 20);
        verify(repository).findLiveByUserId(eq(USER), any(Pageable.class));
        verify(repository, never()).findLiveRefs(anyCollection()); // empty pages need no lookup
    }

    @Test
    void mine_clampsSizeAndNegativePage() {
        when(repository.findByUserId(eq(USER), any(Pageable.class))).thenReturn(Page.empty());

        service.mine(USER, null, -3, 500);
        service.mine(USER, null, 2, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findByUserId(eq(USER), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(50);
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(1);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isEqualTo(2);
    }

    // ── admin list ──────────────────────────────────────────────────────────────

    @Test
    void list_noFilter_pendingFirstQuery_withSuggesterNameMaskedAndJobId() {
        VideoSuggestion live = approved(1, 10L);
        when(repository.findAllPendingFirst(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pending(2), live)));
        when(repository.findLiveRefs(anyCollection())).thenReturn(List.of(new SuggestionLiveRef(1L, 501L)));
        when(userRepo.findAllById(any(Iterable.class))).thenReturn(List.of(user(USER, "bob@example.com")));

        Page<SuggestionDto> page = service.list(null, 0, 20);

        assertThat(page.getContent()).extracting(SuggestionDto::getId).containsExactly(2L, 1L);
        assertThat(page.getContent().get(0).getSuggesterName()).isEqualTo("bob");
        assertThat(page.getContent().get(1).getImportJobId()).isEqualTo(10L);
        assertThat(page.getContent().get(1).isLive()).isTrue();
        assertThat(page.getContent().get(1).getLiveVideoId()).isEqualTo(501L);
    }

    @Test
    void list_statusFilters() {
        when(repository.findAllByStatusNewest(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(repository.findAllLiveNewest(any(Pageable.class))).thenReturn(Page.empty());

        service.list(SuggestionFilter.PENDING, 0, 20);
        verify(repository).findAllByStatusNewest(eq(SuggestionStatus.PENDING), any(Pageable.class));
        service.list(SuggestionFilter.LIVE, 1, 10);
        verify(repository).findAllLiveNewest(any(Pageable.class));
        verifyNoInteractions(userRepo);
    }

    @Test
    void pendingCount_countsPending() {
        when(repository.countByStatus(SuggestionStatus.PENDING)).thenReturn(4L);

        assertThat(service.pendingCount()).isEqualTo(4L);
    }

    @Test
    void submit_facebookShareLink_storesResolvedReel_andKeepsTheOriginalLink() {
        String share = "https://www.facebook.com/share/v/1HLVN1zZ6T/";
        List<String> probed = new ArrayList<>();
        VideoSuggestionServiceImpl withProbe = serviceWithProbe(u -> {
            probed.add(u);
            return Optional.of("https://www.facebook.com/reel/1987308575243562/?rdid=x");
        });

        withProbe.submit(USER, share, null, null);

        ArgumentCaptor<VideoSuggestion> captor = ArgumentCaptor.forClass(VideoSuggestion.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSourceUrl()).isEqualTo(share);
        assertThat(captor.getValue().getNormalizedUrl()).isEqualTo("https://www.facebook.com/reel/1987308575243562");
        assertThat(captor.getValue().getPlatform()).isEqualTo(ImportPlatform.FACEBOOK);
        assertThat(probed).containsExactly(share);
    }

    @Test
    void submit_overDailyLimit_rejectsBeforeProbingTheShareLink() {
        when(repository.countByUserIdAndCreatedAtAfter(eq(USER), any())).thenReturn(5L);
        List<String> probed = new ArrayList<>();
        VideoSuggestionServiceImpl withProbe = serviceWithProbe(u -> {
            probed.add(u);
            return Optional.empty();
        });

        assertError(() -> withProbe.submit(USER, "https://www.facebook.com/share/v/1HLVN1zZ6T/", null, null),
                HttpStatus.TOO_MANY_REQUESTS, "error.suggestion.dailyLimit");
        assertThat(probed).isEmpty();
    }

    @Test
    void approve_unexpandableFbWatch_importsTheStoredUrls_withoutAnyProbe() {
        VideoSuggestion s = pending(5);
        s.setSourceUrl("https://fb.watch/abcDEF_12/?mibextid=1");
        s.setNormalizedUrl("https://fb.watch/abcDEF_12");
        s.setPlatform(ImportPlatform.FACEBOOK);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
        importCreates(9L);
        List<String> probed = new ArrayList<>();
        VideoSuggestionServiceImpl withProbe = serviceWithProbe(u -> {
            probed.add(u);
            return Optional.empty();
        });

        withProbe.approve(5L, null, ADMIN);

        ArgumentCaptor<ImportUrlValidator.ImportUrl> url = ArgumentCaptor.forClass(ImportUrlValidator.ImportUrl.class);
        verify(importService).createResolved(url.capture(), any(), eq(ADMIN));
        assertThat(url.getValue().sourceUrl()).isEqualTo("https://fb.watch/abcDEF_12/?mibextid=1");
        assertThat(url.getValue().normalizedUrl()).isEqualTo("https://fb.watch/abcDEF_12");
        assertThat(url.getValue().platform()).isEqualTo(ImportPlatform.FACEBOOK);
        assertThat(probed).isEmpty();
    }

    // ── approve ─────────────────────────────────────────────────────────────────

    @Test
    void approve_createsExactlyOneImportJob_linksIt_publishesEvent_wakesWorker() {
        VideoSuggestion s = pending(5);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
        when(userRepo.findAllById(any(Iterable.class))).thenReturn(List.of(user(USER, "bob@example.com")));
        importCreates(321L);

        SuggestionDto dto = service.approve(5L, "  New / title ", ADMIN);

        ArgumentCaptor<ImportUrlValidator.ImportUrl> url = ArgumentCaptor.forClass(ImportUrlValidator.ImportUrl.class);
        verify(importService, times(1)).createResolved(url.capture(), eq("New title"), eq(ADMIN));
        assertThat(url.getValue().normalizedUrl()).isEqualTo(YT);
        assertThat(url.getValue().platform()).isEqualTo(s.getPlatform());
        verify(importService, never()).create(any(), any());

        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.APPROVED);
        assertThat(s.getImportJobId()).isEqualTo(321L);
        assertThat(s.getReviewedBy()).isEqualTo(ADMIN);
        assertThat(s.getReviewedAt()).isNotNull();
        assertThat(s.getTitle()).isEqualTo("New title");
        assertThat(dto.getStatus()).isEqualTo(SuggestionStatus.APPROVED);
        assertThat(dto.getImportJobId()).isEqualTo(321L);
        assertThat(dto.getSuggesterName()).isEqualTo("bob");

        ArgumentCaptor<SuggestionReviewedEvent> event = ArgumentCaptor.forClass(SuggestionReviewedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new SuggestionReviewedEvent(5L, USER, true, "New title", null));
        verify(importWorker).wakeUp();
    }

    @Test
    void approve_withoutOverride_keepsSuggestionTitle() {
        VideoSuggestion s = pending(5);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
        importCreates(1L);

        service.approve(5L, null, ADMIN);

        verify(importService).createResolved(any(), eq("Funny cat"), eq(ADMIN));
        assertThat(s.getTitle()).isEqualTo("Funny cat");
    }

    @Test
    void approve_suggestionWithoutTitle_eventSnippetFallsBackToUrl() {
        VideoSuggestion s = pending(5);
        s.setTitle(null);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
        importCreates(1L);

        service.approve(5L, "  ", ADMIN);

        ArgumentCaptor<SuggestionReviewedEvent> event = ArgumentCaptor.forClass(SuggestionReviewedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().snippet()).isEqualTo(YT);
    }

    @Test
    void approve_alreadyReviewed_409_andNothingCreated() {
        VideoSuggestion s = pending(5);
        s.setStatus(SuggestionStatus.APPROVED);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));

        assertError(() -> service.approve(5L, null, ADMIN), HttpStatus.CONFLICT, "error.suggestion.notPending");
        verifyNoInteractions(importService);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(importWorker, never()).wakeUp();
    }

    @Test
    void approve_importDisabled_503_beforeTouchingTheRow() {
        props.getVideoImport().setEnabled(false);

        assertError(() -> service.approve(5L, null, ADMIN), HttpStatus.SERVICE_UNAVAILABLE, "error.import.disabled");
        verifyNoInteractions(repository);
        verifyNoInteractions(importService);
    }

    @Test
    void approve_unknownId_404() {
        when(repository.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertError(() -> service.approve(5L, null, ADMIN), HttpStatus.NOT_FOUND, "error.suggestion.notFound");
    }

    @Test
    void approve_importLineRejected_409WithImportKey_andSuggestionStaysPending() {
        VideoSuggestion s = pending(5);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
        when(importService.createResolved(any(), any(), eq(ADMIN)))
                .thenReturn(CreateVideoImportResultDto.LineResult.builder().line(1).errorCode("DUPLICATE_ACTIVE").build());

        assertError(() -> service.approve(5L, null, ADMIN), HttpStatus.CONFLICT, "error.import.duplicateActive");

        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(s.getImportJobId()).isNull();
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(importWorker, never()).wakeUp();
    }

    @Test
    void approve_importLineErrors_mapToKeys() {
        for (String[] pair : new String[][]{{"INVALID_URL", "error.import.invalidUrl"}, {"WHATEVER", "error.import.failed"}}) {
            VideoSuggestion s = pending(5);
            when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(s));
            when(importService.createResolved(any(), any(), eq(ADMIN)))
                    .thenReturn(CreateVideoImportResultDto.LineResult.builder().line(1).errorCode(pair[0]).build());

            assertError(() -> service.approve(5L, null, ADMIN), HttpStatus.CONFLICT, pair[1]);
        }
    }

    // ── reject ──────────────────────────────────────────────────────────────────

    @Test
    void reject_setsReasonAndNote_publishesEvent() {
        VideoSuggestion s = pending(6);
        when(repository.findByIdForUpdate(6L)).thenReturn(Optional.of(s));

        SuggestionDto dto = service.reject(6L, SuggestionRejectReason.COPYRIGHT, " claimed ", ADMIN);

        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.REJECTED);
        assertThat(s.getRejectReason()).isEqualTo(SuggestionRejectReason.COPYRIGHT);
        assertThat(s.getRejectNote()).isEqualTo("claimed");
        assertThat(s.getReviewedBy()).isEqualTo(ADMIN);
        assertThat(s.getReviewedAt()).isNotNull();
        assertThat(dto.getRejectReason()).isEqualTo(SuggestionRejectReason.COPYRIGHT);
        assertThat(dto.getRejectNote()).isEqualTo("claimed");
        verify(eventPublisher).publishEvent(new SuggestionReviewedEvent(6L, USER, false, "Funny cat", "COPYRIGHT"));
        verifyNoInteractions(importService);
    }

    @Test
    void reject_otherWithoutNote_400() {
        assertError(() -> service.reject(6L, SuggestionRejectReason.OTHER, null, ADMIN),
                HttpStatus.BAD_REQUEST, "error.suggestion.noteRequired");
        assertError(() -> service.reject(6L, SuggestionRejectReason.OTHER, "   ", ADMIN),
                HttpStatus.BAD_REQUEST, "error.suggestion.noteRequired");
        verifyNoInteractions(repository);
    }

    @Test
    void reject_otherWithNote_ok_andOtherReasonsNeedNoNote() {
        when(repository.findByIdForUpdate(6L)).thenReturn(Optional.of(pending(6)));
        service.reject(6L, SuggestionRejectReason.OTHER, "because", ADMIN);

        VideoSuggestion s = pending(7);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(s));
        service.reject(7L, SuggestionRejectReason.DUPLICATE, null, ADMIN);
        assertThat(s.getRejectNote()).isNull();
    }

    @Test
    void reject_alreadyReviewed_409() {
        VideoSuggestion s = pending(6);
        s.setStatus(SuggestionStatus.REJECTED);
        when(repository.findByIdForUpdate(6L)).thenReturn(Optional.of(s));

        assertError(() -> service.reject(6L, SuggestionRejectReason.DUPLICATE, null, ADMIN),
                HttpStatus.CONFLICT, "error.suggestion.notPending");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void reject_unknownId_404() {
        when(repository.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertError(() -> service.reject(6L, SuggestionRejectReason.DUPLICATE, null, ADMIN),
                HttpStatus.NOT_FOUND, "error.suggestion.notFound");
        verify(userRepo, never()).findAllById(any(Iterable.class));
        verify(jobRepository, never()).existsByNormalizedUrlAndStatusIn(anyString(), anyCollection());
    }
}
