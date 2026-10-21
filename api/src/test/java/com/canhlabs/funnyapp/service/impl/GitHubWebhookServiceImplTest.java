package com.canhlabs.funnyapp.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.enums.GitHubIssueState;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.repo.AppReportRepository;
import com.canhlabs.funnyapp.service.GitHubWebhookService.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubWebhookServiceImplTest {

    private static final String SECRET = "test-webhook-secret-xyz";
    private static final String UPDATED = "2026-10-21T03:00:00Z";

    private AppProperties props;
    private AppReportRepository repository;
    private GitHubWebhookServiceImpl service;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getGithub().setWebhookSecret(SECRET);
        repository = mock(AppReportRepository.class);
        service = new GitHubWebhookServiceImpl(props, repository, new ObjectMapper());
        logger = (Logger) LoggerFactory.getLogger(GitHubWebhookServiceImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static byte[] payload(String action, String state, String reason, String repo) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return ("{\"action\":\"" + action + "\",\"issue\":{\"number\":40,\"state\":\"" + state
                + "\",\"state_reason\":" + reasonJson + ",\"updated_at\":\"" + UPDATED + "\"},"
                + "\"repository\":{\"full_name\":\"" + repo + "\"}}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] closed(String reason) {
        return payload("closed", "closed", reason, "nguyenhuuca/assessment");
    }

    // --- verify -------------------------------------------------------------

    @Test
    void verify_acceptsGitHubSignature() throws Exception {
        byte[] body = closed("completed");

        assertThat(service.verify(sign(SECRET, body), body)).isTrue();
    }

    @Test
    void verify_rejectsWrongSecretTamperedBodyMissingOrMalformedHeader() throws Exception {
        byte[] body = closed("completed");
        String good = sign(SECRET, body);

        assertThat(service.verify(sign("other-secret", body), body)).isFalse();
        assertThat(service.verify(good, closed("not_planned"))).isFalse();
        assertThat(service.verify(null, body)).isFalse();
        assertThat(service.verify(good.substring("sha256=".length()), body)).isFalse();
        assertThat(service.verify("sha1=" + good.substring(7), body)).isFalse();
        assertThat(service.verify("sha256=", body)).isFalse();
    }

    @Test
    void verify_blankSecret_isOffAndRejectsEverything() throws Exception {
        byte[] body = closed("completed");
        props.getGithub().setWebhookSecret("");

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.verify(sign("x", body), body)).isFalse();
    }

    // --- handle -------------------------------------------------------------

    @Test
    void handle_closedCompleted_storesClosedWithReasonAndUpdatedAt() {
        when(repository.applyGithubState(40, GitHubIssueState.CLOSED, "completed", Instant.parse(UPDATED)))
                .thenReturn(1);

        assertThat(service.handle("issues", "d-1", closed("completed"))).isEqualTo(Outcome.APPLIED);
    }

    @Test
    void handle_reopened_storesOpenWithReopenedReason() {
        when(repository.applyGithubState(40, GitHubIssueState.OPEN, "reopened", Instant.parse(UPDATED))).thenReturn(1);

        assertThat(service.handle("issues", "d-2", payload("reopened", "open", "reopened", "nguyenhuuca/assessment")))
                .isEqualTo(Outcome.APPLIED);
    }

    @Test
    void handle_unknownOrLongReason_storedAsNull() {
        when(repository.applyGithubState(eq(40), eq(GitHubIssueState.CLOSED), isNull(), any())).thenReturn(1);

        assertThat(service.handle("issues", "d-3", closed("something_new_and_long"))).isEqualTo(Outcome.APPLIED);
        verify(repository).applyGithubState(eq(40), eq(GitHubIssueState.CLOSED), isNull(), any());
    }

    @Test
    void handle_nothingUpdated_existingReport_isStale() {
        when(repository.applyGithubState(anyInt(), any(), any(), any())).thenReturn(0);
        when(repository.existsByStatusAndIssueNumber(ReportStatus.CREATED, 40)).thenReturn(true);

        assertThat(service.handle("issues", "d-4", closed("completed"))).isEqualTo(Outcome.STALE);
    }

    @Test
    void handle_nothingUpdated_noReport_isUnknownIssue() {
        when(repository.applyGithubState(anyInt(), any(), any(), any())).thenReturn(0);
        when(repository.existsByStatusAndIssueNumber(ReportStatus.CREATED, 40)).thenReturn(false);

        assertThat(service.handle("issues", "d-5", closed("completed"))).isEqualTo(Outcome.UNKNOWN_ISSUE);
    }

    @Test
    void handle_pingEvent_isIgnored() {
        assertThat(service.handle("ping", "d-6", "{\"zen\":\"Keep it simple\"}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"labeled", "opened", "edited", "deleted"})
    void handle_otherIssueActions_areIgnored(String action) {
        assertThat(service.handle("issues", "d-7", payload(action, "open", null, "nguyenhuuca/assessment")))
                .isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(repository);
    }

    @Test
    void handle_otherRepository_isIgnored() {
        assertThat(service.handle("issues", "d-8", payload("closed", "closed", "completed", "someone/else")))
                .isEqualTo(Outcome.IGNORED);
        verify(repository, never()).applyGithubState(anyInt(), any(), any(), any());
    }

    @Test
    void handle_repositoryNameMatchIsCaseInsensitive() {
        when(repository.applyGithubState(anyInt(), any(), any(), any())).thenReturn(1);

        assertThat(service.handle("issues", "d-9", payload("closed", "closed", "completed", "NguyenHuuCa/Assessment")))
                .isEqualTo(Outcome.APPLIED);
    }

    @Test
    void handle_notJson_throws() {
        assertThatThrownBy(() -> service.handle("issues", "d", "not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.handle("issues", "d", "[1]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_issuesEventWithoutNumber_throws() {
        byte[] body = "{\"action\":\"closed\",\"issue\":{\"state\":\"closed\"}}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.handle("issues", "d", body)).isInstanceOf(IllegalArgumentException.class);
        byte[] zero = "{\"action\":\"closed\",\"issue\":{\"number\":0}}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.handle("issues", "d", zero)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void handle_badUpdatedAt_throws() {
        byte[] body = ("{\"action\":\"closed\",\"issue\":{\"number\":40,\"state\":\"closed\",\"updated_at\":\"yesterday\"},"
                + "\"repository\":{\"full_name\":\"nguyenhuuca/assessment\"}}").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.handle("issues", "d", body)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    // --- logging --------------------------------------------------------------

    @Test
    void handle_logsDeliveryAndOutcome_neverSecretSignatureOrBody() throws Exception {
        byte[] body = closed("completed");
        String signature = sign(SECRET, body);
        when(repository.applyGithubState(anyInt(), any(), any(), any())).thenReturn(1);

        service.verify(signature, body);
        service.handle("issues", "abc-123", body);

        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("delivery=abc-123")
                && e.getFormattedMessage().contains("issue=40") && e.getFormattedMessage().contains("outcome=APPLIED"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains(SECRET)
                || e.getFormattedMessage().contains(signature.substring(7))
                || e.getFormattedMessage().contains("state_reason"));
    }

    @Test
    void safe_stripsLogInjectionAndCapsLength() {
        assertThat(GitHubWebhookServiceImpl.safe("abc\r\nFAKE log=1")).isEqualTo("abcFAKElog1");
        assertThat(GitHubWebhookServiceImpl.safe("x".repeat(100))).hasSize(64);
        assertThat(GitHubWebhookServiceImpl.safe(null)).isNull();
    }
}
