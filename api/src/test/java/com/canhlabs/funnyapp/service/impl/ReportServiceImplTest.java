package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.GitHubIssueStateCache;
import com.canhlabs.funnyapp.client.GitHubIssueClient;
import com.canhlabs.funnyapp.client.GitHubIssueClient.CreatedIssue;
import com.canhlabs.funnyapp.client.GitHubIssueClient.GitHubException;
import com.canhlabs.funnyapp.client.GitHubIssueClient.IssueState;
import com.canhlabs.funnyapp.dto.admin.CreateReportRequest;
import com.canhlabs.funnyapp.dto.admin.ReportContext;
import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {
    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock AppReportRepository repository;
    @Mock UserRepo userRepo;
    @Mock GitHubIssueClient client;

    GitHubIssueStateCache cache = new GitHubIssueStateCache();
    ReportServiceImpl service;
    User admin;
    User superAdmin;
    /** status/error of every row at the moment it was saved. */
    List<String> saves = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(repository, userRepo, client, cache, Clock.fixed(NOW, ZoneOffset.UTC), "prod-104");
        admin = user(1L, "admin@example.com", UserRole.ADMIN);
        superAdmin = user(2L, "root@example.com", UserRole.SUPER_ADMIN);
        when(userRepo.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepo.findById(2L)).thenReturn(Optional.of(superAdmin));
        when(repository.save(any(AppReport.class))).thenAnswer(inv -> {
            AppReport r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.fromString("6f1c2b0e-1111-2222-3333-444455556666"));
            }
            saves.add(r.getStatus() + "/" + r.getError());
            return r;
        });
        when(client.create(anyString(), anyString(), anyList())).thenReturn(new CreatedIssue(41, "https://github.com/o/r/issues/41"));
        loginAs(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static User user(Long id, String name, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setUserName(name);
        u.setRole(role);
        return u;
    }

    static void loginAs(Long id) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, List.of());
        auth.setDetails(UserDetailDto.builder().id(id).build());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    static CreateReportRequest request() {
        return CreateReportRequest.builder().type("BUG").title("Title").description("Desc")
                .context(ReportContext.builder().page("admin").path("/").appVersion("a1b2c3d")
                        .userAgent("Mozilla").screenWidth(390).build()).build();
    }

    void capCount(long n) {
        when(repository.countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(
                eq(ReportStatus.CREATED), eq(UserRole.ADMIN), any())).thenReturn(n);
    }

    @SuppressWarnings("unchecked")
    List<String> sentLabels() {
        ArgumentCaptor<List<String>> c = ArgumentCaptor.forClass(List.class);
        verify(client).create(anyString(), anyString(), c.capture());
        return c.getValue();
    }

    String sentBody() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(client).create(anyString(), c.capture(), anyList());
        return c.getValue();
    }

    void assertCode(Runnable call, HttpStatus status, String key) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessageKey()).isEqualTo(key);
        });
        verify(repository, never()).save(any());
    }

    // ---- create: happy path, labels, store-then-call

    @Test
    void create_bugAsAdmin_storesPendingThenCreated() {
        capCount(0);
        ReportDto dto = service.create(request());

        assertThat(saves).containsExactly("FAILED/pending", "CREATED/null");
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.CREATED);
        assertThat(dto.getIssueNumber()).isEqualTo(41);
        assertThat(dto.getIssueUrl()).isEqualTo("https://github.com/o/r/issues/41");
        assertThat(dto.getLabels()).containsExactly("autopilot", "bug");
        assertThat(dto.isAutopilot()).isTrue();
        assertThat(dto.isCapReached()).isFalse();
        assertThat(dto.getReporter()).isEqualTo("admin@example.com");
        assertThat(dto.getGithubState()).isEqualTo("OPEN");
        assertThat(dto.getError()).isNull();
    }

    /** Prod 2026-10-20: save() merges the existing row and returns a copy with a detached lazy reporter. */
    @Test
    void create_saveReturnsMergedCopyWithDetachedReporter_dtoStillHasReporter() {
        capCount(0);
        User detached = org.mockito.Mockito.mock(User.class);
        when(detached.getUserName()).thenThrow(new org.hibernate.LazyInitializationException("no session"));
        when(repository.save(any(AppReport.class))).thenAnswer(inv -> {
            AppReport r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.fromString("6f1c2b0e-1111-2222-3333-444455556666"));
                return r;
            }
            AppReport merged = AppReport.builder().id(r.getId()).status(r.getStatus()).reporter(detached).build();
            return merged;
        });

        ReportDto dto = service.create(request());

        assertThat(dto.getReporter()).isEqualTo("admin@example.com");
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.CREATED);
        assertThat(dto.getIssueNumber()).isEqualTo(41);
    }

    @Test
    void create_typeLabels() {
        capCount(0);
        CreateReportRequest r = request();
        r.setType("ENHANCEMENT");
        assertThat(service.create(r).getLabels()).containsExactly("autopilot", "enhancement");
        r.setType("ISSUE");
        assertThat(service.create(r).getLabels()).containsExactly("autopilot", "report");
    }

    @Test
    void create_bodyHasContextFooterMarkerAndNoIdentity() {
        capCount(0);
        service.create(request());

        String body = sentBody();
        assertThat(body).startsWith("Desc\n\n### Context\n");
        assertThat(body).contains("- Page: admin", "- Path: /", "- App: a1b2c3d", "- API: prod-104", "- Browser: Mozilla", "- Screen: 390px");
        assertThat(body).contains("Reported in-app by an ADMIN · report 6f1c2b0e");
        assertThat(body).endsWith("<!-- app-report:6f1c2b0e-1111-2222-3333-444455556666 -->");
        assertThat(body).doesNotContain("admin@example.com").doesNotContain("@example.com");
    }

    @Test
    void create_missingContextPrintsUnknown() {
        capCount(0);
        CreateReportRequest r = request();
        r.setContext(null);
        service.create(r);

        assertThat(sentBody()).contains("- Page: unknown", "- Path: unknown", "- App: unknown", "- API: prod-104",
                "- Browser: unknown", "- Screen: unknown");
    }

    @Test
    void create_pathQueryAndFragmentStripped_ec11() {
        capCount(0);
        CreateReportRequest r = request();
        r.getContext().setPath("/?token=abc#x");
        service.create(r);

        ArgumentCaptor<AppReport> saved = ArgumentCaptor.forClass(AppReport.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPagePath()).isEqualTo("/");
        assertThat(sentBody()).contains("- Path: /\n").doesNotContain("token");
    }

    @Test
    void create_apiVersionComesFromServerNotClient() {
        capCount(0);
        CreateReportRequest r = request();
        r.getContext().setAppVersion("prod-999");
        service.create(r);

        ArgumentCaptor<AppReport> saved = ArgumentCaptor.forClass(AppReport.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getApiVersion()).isEqualTo("prod-104");
        assertThat(sentBody()).contains("- App: prod-999\n", "- API: prod-104\n");
    }

    @Test
    void create_fragmentBeforeQuery_cutsAtFirst() {
        capCount(0);
        CreateReportRequest r = request();
        r.getContext().setPath("/a#frag?x=1");
        service.create(r);
        assertThat(sentBody()).contains("- Path: /a\n");
    }

    @Test
    void create_titleAndDescriptionAreTrimmed() {
        capCount(0);
        CreateReportRequest r = request();
        r.setTitle("  Hi  ");
        r.setDescription("  Text \n");
        service.create(r);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(client).create(title.capture(), anyString(), anyList());
        assertThat(title.getValue()).isEqualTo("Hi");
        assertThat(sentBody()).startsWith("Text\n\n");
    }

    // ---- BR-3 / BR-4 / EC-4

    @Test
    void cap_at9_adminStillGetsAutopilot() {
        capCount(9);
        ReportDto dto = service.create(request());
        assertThat(dto.getLabels()).containsExactly("autopilot", "bug");
        assertThat(dto.isCapReached()).isFalse();
    }

    @Test
    void cap_at10_adminLosesAutopilotAndCapReached() {
        capCount(10);
        ReportDto dto = service.create(request());
        assertThat(dto.getLabels()).containsExactly("bug");
        assertThat(dto.isAutopilot()).isFalse();
        assertThat(dto.isCapReached()).isTrue();
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.CREATED);
    }

    @Test
    void cap_countsLast24HoursOfCreatedAdminAutopilot() {
        capCount(0);
        service.create(request());
        verify(repository).countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(
                ReportStatus.CREATED, UserRole.ADMIN, NOW.minusSeconds(24 * 3600));
    }

    @Test
    void superAdmin_notCappedAndGetsAutoLabel() {
        loginAs(2L);
        capCount(99);
        CreateReportRequest r = request();
        r.setAutoLabel("AUTO");
        ReportDto dto = service.create(r);

        assertThat(dto.getLabels()).containsExactly("autopilot", "autopilot:auto", "bug");
        assertThat(dto.isCapReached()).isFalse();
        assertThat(dto.getAutoLabel()).isEqualTo(ReportAutoLabel.AUTO);
        verify(repository, never()).countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(any(), any(), any());
        assertThat(sentBody()).contains("by the SUPER_ADMIN");
    }

    @Test
    void superAdmin_autoMergeLabel() {
        loginAs(2L);
        CreateReportRequest r = request();
        r.setAutoLabel("AUTO_MERGE");
        assertThat(service.create(r).getLabels()).containsExactly("autopilot", "autopilot:auto-merge", "bug");
    }

    @Test
    void admin_autoLabel_forbiddenAndNothingStored() {
        CreateReportRequest r = request();
        r.setAutoLabel("AUTO");
        assertCode(() -> service.create(r), HttpStatus.FORBIDDEN, "error.report.autoLabelForbidden");
        verify(client, never()).create(anyString(), anyString(), anyList());
    }

    @Test
    void invalidAutoLabel_400() {
        CreateReportRequest r = request();
        r.setAutoLabel("NOPE");
        assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidAutoLabel");
    }

    // ---- EC-1 / EC-2

    @Test
    void notConfigured_ec1_keepsFailedRow() {
        capCount(0);
        when(client.create(anyString(), anyString(), anyList())).thenThrow(new GitHubException("github.notConfigured"));
        ReportDto dto = service.create(request());

        assertThat(dto.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(dto.getError()).isEqualTo("github.notConfigured");
        assertThat(dto.getIssueNumber()).isNull();
        assertThat(dto.getGithubState()).isNull();
        assertThat(saves).containsExactly("FAILED/pending", "FAILED/github.notConfigured");
    }

    @Test
    void githubHttpError_ec2() {
        capCount(0);
        when(client.create(anyString(), anyString(), anyList())).thenThrow(new GitHubException("github.http.502"));
        assertThat(service.create(request()).getError()).isEqualTo("github.http.502");
    }

    @Test
    void unexpectedException_isFailedNotThrown() {
        capCount(0);
        when(client.create(anyString(), anyString(), anyList())).thenThrow(new IllegalStateException("boom secret-token"));
        ReportDto dto = service.create(request());
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(dto.getError()).isEqualTo("github.io").doesNotContain("secret");
    }

    // ---- EC-10 validation

    @Test
    void boundaries_ec10() {
        capCount(0);
        CreateReportRequest ok = request();
        ok.setTitle("t".repeat(120));
        ok.setDescription("d".repeat(5000));
        assertThat(service.create(ok).getStatus()).isEqualTo(ReportStatus.CREATED);
    }

    @Test
    void title121_whitespace_and_missing_400() {
        for (String t : new String[]{"t".repeat(121), "   ", null}) {
            CreateReportRequest r = request();
            r.setTitle(t);
            assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidTitle");
        }
    }

    @Test
    void description5001_and_blank_400() {
        for (String d : new String[]{"d".repeat(5001), " \n ", null}) {
            CreateReportRequest r = request();
            r.setDescription(d);
            assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidDescription");
        }
    }

    @Test
    void invalidType_400() {
        for (String t : new String[]{null, "", "bug", "OTHER"}) {
            CreateReportRequest r = request();
            r.setType(t);
            assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidType");
        }
        assertCode(() -> service.create(null), HttpStatus.BAD_REQUEST, "error.report.invalidType");
    }

    @Test
    void invalidContext_400() {
        List<java.util.function.Consumer<ReportContext>> bad = List.of(
                c -> c.setPath("no-slash"),
                c -> c.setPath("/" + "p".repeat(300)),
                c -> c.setPage("p".repeat(33)),
                c -> c.setAppVersion("v".repeat(41)),
                c -> c.setUserAgent("u".repeat(301)),
                c -> c.setScreenWidth(0),
                c -> c.setScreenWidth(10_001));
        for (var mutate : bad) {
            CreateReportRequest r = request();
            mutate.accept(r.getContext());
            assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidContext");
        }
    }

    @Test
    void contextBoundariesAccepted() {
        capCount(0);
        CreateReportRequest r = request();
        r.getContext().setPath("/" + "p".repeat(299));
        r.getContext().setPage("p".repeat(32));
        r.getContext().setAppVersion("v".repeat(40));
        r.getContext().setUserAgent("u".repeat(300));
        r.getContext().setScreenWidth(10_000);
        assertThat(service.create(r).getStatus()).isEqualTo(ReportStatus.CREATED);
    }

    @Test
    void noAuthenticatedUser_accessDenied() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    // ---- control characters (CWE-74)

    @Test
    void create_controlCharsInContext_400() {
        for (String bad : List.of("a\nb", "a\rb", "a\u0000b", "a\u001fb")) {
            for (int field = 0; field < 4; field++) {
                CreateReportRequest r = request();
                ReportContext c = r.getContext();
                switch (field) {
                    case 0 -> c.setPage(bad);
                    case 1 -> c.setPath("/" + bad);
                    case 2 -> c.setAppVersion(bad);
                    default -> c.setUserAgent(bad);
                }
                assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidContext");
            }
        }
    }

    @Test
    void create_controlCharsInTitle_400_descriptionMayKeepNewlines() {
        CreateReportRequest r = request();
        r.setTitle("Line1\nLine2");
        assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidTitle");
        r.setTitle("Line1\rLine2");
        assertCode(() -> service.create(r), HttpStatus.BAD_REQUEST, "error.report.invalidTitle");

        capCount(0);
        CreateReportRequest ok = request();
        ok.setDescription("a\nb");
        assertThat(service.create(ok).getStatus()).isEqualTo(ReportStatus.CREATED);
    }

    // ---- retry

    AppReport failedRow(User reporter, UserRole role) {
        return AppReport.builder().id(UUID.fromString("6f1c2b0e-1111-2222-3333-444455556666"))
                .reporter(reporter).reporterRole(role).type(ReportType.BUG).title("T").description("D")
                .page("admin").pagePath("/").status(ReportStatus.FAILED).error("github.timeout").build();
    }

    @Test
    void retry_byReporter_turnsRowCreated() {
        AppReport row = failedRow(admin, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(0);

        ReportDto dto = service.retry(row.getId());

        assertThat(dto.getStatus()).isEqualTo(ReportStatus.CREATED);
        assertThat(dto.getError()).isNull();
        assertThat(dto.getIssueNumber()).isEqualTo(41);
        assertThat(dto.getLabels()).containsExactly("autopilot", "bug");
    }

    @Test
    void retry_reappliesCap() {
        AppReport row = failedRow(admin, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(10);

        ReportDto dto = service.retry(row.getId());
        assertThat(dto.getLabels()).containsExactly("bug");
        assertThat(dto.isCapReached()).isTrue();
    }

    @Test
    void retry_reappliesStoredAutoLabelForSuperAdmin() {
        loginAs(2L);
        AppReport row = failedRow(superAdmin, UserRole.SUPER_ADMIN);
        row.setAutoLabel(ReportAutoLabel.AUTO_MERGE);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));

        assertThat(service.retry(row.getId()).getLabels())
                .containsExactly("autopilot", "autopilot:auto-merge", "bug");
    }

    @Test
    void retry_createdRow_409_noGithubCall_ec5() {
        AppReport row = failedRow(admin, UserRole.ADMIN);
        row.setStatus(ReportStatus.CREATED);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));

        assertCode(() -> service.retry(row.getId()), HttpStatus.CONFLICT, "error.report.alreadyCreated");
        verify(client, never()).create(anyString(), anyString(), anyList());
    }

    @Test
    void retry_otherAdminOnCreatedRow_403_not409() {
        User other = user(3L, "other@example.com", UserRole.ADMIN);
        AppReport row = failedRow(other, UserRole.ADMIN);
        row.setStatus(ReportStatus.CREATED);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));

        assertCode(() -> service.retry(row.getId()), HttpStatus.FORBIDDEN, "error.report.retryForbidden");
    }

    @Test
    void retry_adminOwnRowWithStoredAutoLabel_sendsNoAutoLabelAndIsCapped() {
        AppReport row = failedRow(admin, UserRole.ADMIN);
        row.setAutoLabel(ReportAutoLabel.AUTO_MERGE);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(10);

        ReportDto dto = service.retry(row.getId());
        assertThat(dto.getLabels()).containsExactly("bug");
        assertThat(dto.isCapReached()).isTrue();
    }

    @Test
    void retry_adminOwnRowStoredAsSuperAdmin_followsCallerRole() {
        AppReport row = failedRow(admin, UserRole.SUPER_ADMIN);
        row.setAutoLabel(ReportAutoLabel.AUTO_MERGE);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(0);

        assertThat(service.retry(row.getId()).getLabels()).containsExactly("autopilot", "bug");
    }

    @Test
    void retry_unknownId_404() {
        UUID id = UUID.randomUUID();
        when(repository.findWithReporterById(id)).thenReturn(Optional.empty());
        assertCode(() -> service.retry(id), HttpStatus.NOT_FOUND, "error.report.notFound");
    }

    @Test
    void retry_otherAdmin_403() {
        User other = user(3L, "other@example.com", UserRole.ADMIN);
        AppReport row = failedRow(other, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));

        assertCode(() -> service.retry(row.getId()), HttpStatus.FORBIDDEN, "error.report.retryForbidden");
        verify(client, never()).create(anyString(), anyString(), anyList());
    }

    @Test
    void retry_superAdminMayRetryOthers() {
        loginAs(2L);
        AppReport row = failedRow(admin, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(0);

        assertThat(service.retry(row.getId()).getStatus()).isEqualTo(ReportStatus.CREATED);
    }

    @Test
    void retry_deletedReporter_ec7_adminForbiddenSuperAdminAllowed() {
        AppReport row = failedRow(null, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        assertCode(() -> service.retry(row.getId()), HttpStatus.FORBIDDEN, "error.report.retryForbidden");

        loginAs(2L);
        capCount(0);
        ReportDto dto = service.retry(row.getId());
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.CREATED);
        assertThat(dto.getReporter()).isNull();
    }

    @Test
    void retry_githubFailsAgain_staysFailed() {
        AppReport row = failedRow(admin, UserRole.ADMIN);
        when(repository.findWithReporterById(row.getId())).thenReturn(Optional.of(row));
        capCount(0);
        when(client.create(anyString(), anyString(), anyList())).thenThrow(new GitHubException("github.timeout"));

        ReportDto dto = service.retry(row.getId());
        assertThat(dto.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(dto.getError()).isEqualTo("github.timeout");
    }

    // ---- list

    AppReport createdRow(int number, User reporter) {
        AppReport r = failedRow(reporter, UserRole.ADMIN);
        r.setId(UUID.randomUUID());
        r.setStatus(ReportStatus.CREATED);
        r.setError(null);
        r.setIssueNumber(number);
        r.setIssueUrl("https://github.com/o/r/issues/" + number);
        r.setLabels("autopilot,bug");
        r.setAutopilot(true);
        return r;
    }

    void rows(AppReport... rows) {
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(rows), inv.getArgument(0), 7));
    }

    @Test
    void list_invalidPageOrSize_400() {
        int[][] bad = {{-1, 20}, {0, 0}, {0, 51}};
        for (int[] b : bad) {
            assertThatThrownBy(() -> service.list(b[0], b[1])).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getMessageKey()).isEqualTo("error.report.invalidPage"));
        }
        verify(repository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void list_maxSizeAccepted() {
        rows();
        assertThat(service.list(0, 50).getContent()).isEmpty();
    }

    @Test
    void list_statesFromCacheClientAndFailed() {
        AppReport cached = createdRow(10, admin);
        AppReport fresh = createdRow(11, admin);
        AppReport closed = createdRow(12, null);
        AppReport failed = failedRow(admin, UserRole.ADMIN);
        cache.put(10, IssueState.CLOSED);
        when(client.state(11)).thenReturn(IssueState.OPEN);
        when(client.state(12)).thenReturn(IssueState.CLOSED);
        rows(cached, fresh, closed, failed);

        Page<ReportDto> page = service.list(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(ReportDto::getGithubState)
                .containsExactly("CLOSED", "OPEN", "CLOSED", null);
        assertThat(page.getContent().get(2).getReporter()).isNull();
        assertThat(page.getContent().get(0).getReporter()).isEqualTo("admin@example.com");
        verify(client, never()).state(10);
        assertThat(cache.get(11)).contains(IssueState.OPEN);
    }

    @Test
    void list_partialFailure_ec9_unknownNotCached() {
        AppReport ok = createdRow(20, admin);
        AppReport thrown = createdRow(21, admin);
        AppReport unknown = createdRow(22, admin);
        when(client.state(20)).thenReturn(IssueState.OPEN);
        when(client.state(21)).thenThrow(new IllegalStateException("x"));
        when(client.state(22)).thenReturn(IssueState.UNKNOWN);
        rows(ok, thrown, unknown);

        Page<ReportDto> page = service.list(0, 20);

        assertThat(page.getContent()).extracting(ReportDto::getGithubState)
                .containsExactly("OPEN", "UNKNOWN", "UNKNOWN");
        assertThat(cache.get(21)).isEmpty();
        assertThat(cache.get(22)).isEmpty();
    }

    @Test
    void list_storedWebhookState_usedWithoutGitHubCall_readThroughForTheRest() {
        AppReport stored = createdRow(30, admin);
        stored.setGithubState(GitHubIssueState.CLOSED);
        stored.setGithubStateReason("not_planned");
        AppReport reopened = createdRow(31, admin);
        reopened.setGithubState(GitHubIssueState.OPEN);
        reopened.setGithubStateReason("reopened");
        AppReport legacy = createdRow(32, admin);
        AppReport failed = failedRow(admin, UserRole.ADMIN);
        failed.setGithubStateReason("completed");
        when(client.state(32)).thenReturn(IssueState.CLOSED);
        rows(stored, reopened, legacy, failed);

        Page<ReportDto> page = service.list(0, 20);

        assertThat(page.getContent()).extracting(ReportDto::getGithubState)
                .containsExactly("CLOSED", "OPEN", "CLOSED", null);
        assertThat(page.getContent()).extracting(ReportDto::getGithubStateReason)
                .containsExactly("not_planned", "reopened", null, null);
        verify(client, never()).state(30);
        verify(client, never()).state(31);
        verify(client).state(32);
    }

    @Test
    void list_failedRowsOnly_noStateCalls() {
        rows(failedRow(admin, UserRole.ADMIN));
        assertThat(service.list(0, 20).getContent().get(0).getGithubState()).isNull();
        verify(client, never()).state(anyInt());
    }
}
