package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.GitHubIssueStateCache;
import com.canhlabs.funnyapp.client.GitHubIssueClient;
import com.canhlabs.funnyapp.client.GitHubIssueClient.CreatedIssue;
import com.canhlabs.funnyapp.client.GitHubIssueClient.IssueState;
import com.canhlabs.funnyapp.dto.admin.CreateReportRequest;
import com.canhlabs.funnyapp.dto.admin.ReportContext;
import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.ReportService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.TaskUtils;
import com.canhlabs.funnyapp.utils.TaskUtils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Store-then-call: the row is saved as FAILED/'pending' first, GitHub is called outside any transaction
 * (each repository call commits on its own), then the outcome is saved.
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 5000;
    static final int MAX_PAGE = 32;
    static final int MAX_PATH = 300;
    static final int MAX_APP_VERSION = 40;
    static final int MAX_USER_AGENT = 300;
    static final int MAX_SCREEN_WIDTH = 10_000;
    static final int MAX_PAGE_SIZE = 50;
    static final int ADMIN_AUTOPILOT_CAP = 10;
    static final Duration CAP_WINDOW = Duration.ofHours(24);
    static final String PENDING = "pending";
    private static final int MAX_ERROR = 64;

    private final AppReportRepository repository;
    private final UserRepo userRepo;
    private final GitHubIssueClient client;
    private final GitHubIssueStateCache stateCache;
    private final Clock clock;

    @Autowired
    public ReportServiceImpl(AppReportRepository repository, UserRepo userRepo,
                             GitHubIssueClient client, GitHubIssueStateCache stateCache) {
        this(repository, userRepo, client, stateCache, Clock.systemUTC());
    }

    ReportServiceImpl(AppReportRepository repository, UserRepo userRepo,
                      GitHubIssueClient client, GitHubIssueStateCache stateCache, Clock clock) {
        this.repository = repository;
        this.userRepo = userRepo;
        this.client = client;
        this.stateCache = stateCache;
        this.clock = clock;
    }

    @Override
    public ReportDto create(CreateReportRequest request) {
        User reporter = currentUser();
        if (request == null) {
            request = new CreateReportRequest();
        }
        ReportType type = parseType(request.getType());
        String title = trimmed(request.getTitle(), MAX_TITLE, "error.report.invalidTitle");
        String description = trimmed(request.getDescription(), MAX_DESCRIPTION, "error.report.invalidDescription");
        ReportContext context = request.getContext() == null ? new ReportContext() : request.getContext();
        validateContext(context);
        ReportAutoLabel autoLabel = parseAutoLabel(request.getAutoLabel());
        if (autoLabel != null && reporter.getRole() != UserRole.SUPER_ADMIN) {
            throw CustomException.of(HttpStatus.FORBIDDEN, 403, "error.report.autoLabelForbidden");
        }

        AppReport row = repository.save(AppReport.builder()
                .reporter(reporter)
                .reporterRole(reporter.getRole())
                .type(type)
                .title(title)
                .description(description)
                .page(context.getPage())
                .pagePath(ReportIssueContent.stripPath(context.getPath()))
                .appVersion(context.getAppVersion())
                .userAgent(context.getUserAgent())
                .screenWidth(context.getScreenWidth())
                .autoLabel(autoLabel)
                .status(ReportStatus.FAILED)
                .error(PENDING)
                .build());
        return toDto(callGitHub(row), IssueState.OPEN.name());
    }

    @Override
    public Page<ReportDto> list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.report.invalidPage");
        }
        Page<AppReport> rows = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<IssueState> states = resolveStates(rows.getContent());
        List<AppReport> content = rows.getContent();
        List<ReportDto> dtos = new ArrayList<>(content.size());
        for (int i = 0; i < content.size(); i++) {
            IssueState state = states.get(i);
            dtos.add(toDto(content.get(i), state == null ? null : state.name()));
        }
        return new PageImpl<>(dtos, rows.getPageable(), rows.getTotalElements());
    }

    @Override
    public ReportDto retry(UUID id) {
        User caller = currentUser();
        AppReport row = repository.findWithReporterById(id)
                .orElseThrow(() -> CustomException.of(HttpStatus.NOT_FOUND, 404, "error.report.notFound"));
        if (row.getStatus() == ReportStatus.CREATED) {
            throw CustomException.of(HttpStatus.CONFLICT, 409, "error.report.alreadyCreated");
        }
        boolean isReporter = row.getReporter() != null && row.getReporter().getId().equals(caller.getId());
        if (!isReporter && caller.getRole() != UserRole.SUPER_ADMIN) {
            throw CustomException.of(HttpStatus.FORBIDDEN, 403, "error.report.retryForbidden");
        }
        return toDto(callGitHub(row), IssueState.OPEN.name());
    }

    /** Applies the cap, calls GitHub and saves the outcome; never throws for a GitHub failure. */
    private AppReport callGitHub(AppReport row) {
        boolean autopilot = !isCapped(row);
        List<String> labels = ReportIssueContent.labels(row.getType(), row.getAutoLabel(), autopilot);
        try {
            CreatedIssue issue = client.create(row.getTitle(), ReportIssueContent.body(row), labels);
            row.setStatus(ReportStatus.CREATED);
            row.setIssueNumber(issue.number());
            row.setIssueUrl(issue.htmlUrl());
            row.setLabels(String.join(",", labels));
            row.setAutopilot(autopilot);
            row.setError(null);
        } catch (GitHubIssueClient.GitHubException e) {
            markFailed(row, e.getCode());
        } catch (RuntimeException e) {
            log.warn("GitHub call failed for report {}: {}", row.getId(), e.getClass().getSimpleName());
            markFailed(row, "github.io");
        }
        return repository.save(row);
    }

    private static void markFailed(AppReport row, String code) {
        row.setStatus(ReportStatus.FAILED);
        row.setIssueNumber(null);
        row.setIssueUrl(null);
        row.setError(code == null ? "github.io" : code.substring(0, Math.min(code.length(), MAX_ERROR)));
    }

    /** BR-3: only ADMIN reports are capped; the count takes no lock (EC-3). */
    private boolean isCapped(AppReport row) {
        if (row.getReporterRole() != UserRole.ADMIN) {
            return false;
        }
        long used = repository.countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(
                ReportStatus.CREATED, UserRole.ADMIN, clock.instant().minus(CAP_WINDOW));
        return used >= ADMIN_AUTOPILOT_CAP;
    }

    /** One entry per row: null for FAILED rows, otherwise the cached or freshly fetched state. */
    private List<IssueState> resolveStates(List<AppReport> rows) {
        IssueState[] states = new IssueState[rows.size()];
        List<Integer> missing = new ArrayList<>();
        List<Callable<IssueState>> tasks = new ArrayList<>();
        Optional<IssueState> cached;
        for (int i = 0; i < rows.size(); i++) {
            AppReport row = rows.get(i);
            if (row.getStatus() != ReportStatus.CREATED) {
                continue;
            }
            Integer number = row.getIssueNumber();
            if (number == null) {
                states[i] = IssueState.UNKNOWN;
            } else if ((cached = stateCache.get(number)).isPresent()) {
                states[i] = cached.get();
            } else {
                missing.add(i);
                tasks.add(() -> client.state(number));
            }
        }
        List<Result<IssueState>> results = TaskUtils.runAllIgnoreError(tasks);
        for (int k = 0; k < missing.size(); k++) {
            int i = missing.get(k);
            Result<IssueState> result = results.get(k);
            IssueState state = result.isSuccess() && result.getValue() != null ? result.getValue() : IssueState.UNKNOWN;
            states[i] = state;
            stateCache.put(rows.get(i).getIssueNumber(), state);
        }
        return Arrays.asList(states);
    }

    private User currentUser() {
        UserDetailDto detail = AppUtils.getCurrentUser();
        if (detail == null || detail.getId() == null) {
            throw new AccessDeniedException("Access denied");
        }
        return userRepo.findById(detail.getId()).orElseThrow(() -> new AccessDeniedException("Access denied"));
    }

    private static ReportType parseType(String type) {
        if (type != null) {
            try {
                return ReportType.valueOf(type);
            } catch (IllegalArgumentException ignored) {
                // falls through to the 400 below
            }
        }
        throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.report.invalidType");
    }

    private static ReportAutoLabel parseAutoLabel(String autoLabel) {
        if (autoLabel == null) {
            return null;
        }
        try {
            return ReportAutoLabel.valueOf(autoLabel);
        } catch (IllegalArgumentException e) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.report.invalidAutoLabel");
        }
    }

    private static String trimmed(String value, int max, String errorKey) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, errorKey);
        }
        return text;
    }

    private static void validateContext(ReportContext c) {
        boolean valid = within(c.getPage(), MAX_PAGE)
                && within(c.getAppVersion(), MAX_APP_VERSION)
                && within(c.getUserAgent(), MAX_USER_AGENT)
                && (c.getPath() == null || (c.getPath().startsWith("/") && c.getPath().length() <= MAX_PATH))
                && (c.getScreenWidth() == null || (c.getScreenWidth() >= 1 && c.getScreenWidth() <= MAX_SCREEN_WIDTH));
        if (!valid) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.report.invalidContext");
        }
    }

    private static boolean within(String value, int max) {
        return value == null || value.length() <= max;
    }

    private static ReportDto toDto(AppReport row, String githubState) {
        boolean created = row.getStatus() == ReportStatus.CREATED;
        String labels = row.getLabels();
        return ReportDto.builder()
                .id(row.getId())
                .type(row.getType())
                .title(row.getTitle())
                .status(row.getStatus())
                .issueNumber(row.getIssueNumber())
                .issueUrl(row.getIssueUrl())
                .labels(labels == null || labels.isEmpty() ? List.of() : List.of(labels.split(",")))
                .autopilot(row.isAutopilot())
                .autoLabel(row.getAutoLabel())
                .capReached(created && !row.isAutopilot())
                .error(row.getError())
                .reporter(row.getReporter() == null ? null : row.getReporter().getUserName())
                .githubState(created ? githubState : null)
                .createdAt(row.getCreatedAt())
                .build();
    }
}
