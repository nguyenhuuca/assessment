package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.event.ReportClosedEvent;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.service.GitHubWebhookService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Set;

/** HMAC-verified GitHub webhook; only issue closed/reopened events change anything (ADR-0026 D7). */
@Slf4j
@Service
public class GitHubWebhookServiceImpl implements GitHubWebhookService {

    static final String SIGNATURE_PREFIX = "sha256=";
    static final Set<String> ACTIONS = Set.of("closed", "reopened");
    static final Set<String> REASONS = Set.of("completed", "not_planned", "duplicate", "reopened");
    private static final int LOG_MAX = 64;

    private final AppProperties props;
    private final AppReportRepository repository;
    private final JsonMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubWebhookServiceImpl(AppProperties props, AppReportRepository repository, JsonMapper mapper,
                                    ApplicationEventPublisher eventPublisher) {
        this.props = props;
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean isConfigured() {
        return props.getGithub().isWebhookConfigured();
    }

    @Override
    public boolean verify(String signatureHeader, byte[] body) {
        if (!isConfigured() || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] given = signatureHeader.substring(SIGNATURE_PREFIX.length()).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = hmacHex(body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    @Override
    public Outcome handle(String event, String deliveryId, byte[] body) {
        JsonNode root = parse(body);
        if (!"issues".equals(event)) {
            return logged(deliveryId, event, null, null, Outcome.IGNORED);
        }
        JsonNode issue = root.path("issue");
        if (!issue.path("number").canConvertToInt() || !issue.path("number").isIntegralNumber()
                || issue.path("number").intValue() < 1) {
            throw new IllegalArgumentException("issue.number missing");
        }
        int number = issue.path("number").intValue();
        String action = root.path("action").asText("");
        GitHubIssueState state = stateOf(issue.path("state").asText(""));
        if (!ACTIONS.contains(action) || state == null || !isOurRepo(root)) {
            return logged(deliveryId, event, action, number, Outcome.IGNORED);
        }
        Instant at = updatedAt(issue);
        String raw = issue.path("state_reason").asText(null);
        String reason = REASONS.contains(raw) ? raw : null;
        int changed = repository.applyGithubState(number, state, reason, at);
        Outcome outcome = changed > 0 ? Outcome.APPLIED
                : repository.existsByStatusAndIssueNumber(ReportStatus.CREATED, number) ? Outcome.STALE
                : Outcome.UNKNOWN_ISSUE;
        if (outcome == Outcome.APPLIED && state == GitHubIssueState.CLOSED) {
            eventPublisher.publishEvent(new ReportClosedEvent(number, reason));
        }
        return logged(deliveryId, event, action, number, outcome);
    }

    private String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getGithub().getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("body is not a JSON object");
            }
            return root;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("body is not JSON");
        }
    }

    private static GitHubIssueState stateOf(String state) {
        return switch (state) {
            case "open" -> GitHubIssueState.OPEN;
            case "closed" -> GitHubIssueState.CLOSED;
            default -> null;
        };
    }

    private boolean isOurRepo(JsonNode root) {
        AppProperties.Github gh = props.getGithub();
        return (gh.getOwner() + "/" + gh.getRepo()).equalsIgnoreCase(root.path("repository").path("full_name").asText(""));
    }

    private static Instant updatedAt(JsonNode issue) {
        try {
            return Instant.parse(issue.path("updated_at").asText(""));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("issue.updated_at invalid");
        }
    }

    private static Outcome logged(String deliveryId, String event, String action, Integer number, Outcome outcome) {
        log.info("GitHub webhook delivery={} event={} action={} issue={} outcome={}",
                safe(deliveryId), safe(event), safe(action), number, outcome);
        return outcome;
    }

    /** Header and payload text is logged only as short [A-Za-z0-9_-] tokens (no log injection). */
    static String safe(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("[^A-Za-z0-9_-]", "");
        return clean.length() > LOG_MAX ? clean.substring(0, LOG_MAX) : clean;
    }
}
