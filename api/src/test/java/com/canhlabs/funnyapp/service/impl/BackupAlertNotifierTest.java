package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.MailService;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BackupAlertNotifierTest {

    private MailService mail;
    private AppProperties props;
    private BackupAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        mail = mock(MailService.class);
        props = new AppProperties();
        props.getGoogleOauth().setClientSecret("cs-secret");
        props.getGoogleOauth().setRefreshToken("rt-secret");
        notifier = new BackupAlertNotifier(mail, props, "sender@example.com", "db-pass");
    }

    private String[] capture() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).sendSimpleMail(to.capture(), subject.capture(), body.capture());
        return new String[]{to.getValue(), subject.getValue(), body.getValue()};
    }

    @Test
    void failureHasSubjectBodyAndHintPerCode() {
        notifier.failure(BackupTier.WEEKLY, new BackupException(BackupErrorCode.DRIVE_AUTH_FAILED,
                BackupStep.UPLOAD, "invalid_grant"), "2026-11-16T20:00:00Z");
        String[] m = capture();
        assertThat(m[1]).isEqualTo("[Funny Movies] DB backup FAILED (weekly): DRIVE_AUTH_FAILED");
        assertThat(m[2]).contains("Tier: weekly", "Step: UPLOAD", "Code: DRIVE_AUTH_FAILED", "Time (UTC): ",
                "Reason: invalid_grant", "GOOGLE_OAUTH_REFRESH_TOKEN", "Last good backup: 2026-11-16T20:00:00Z");
    }

    @Test
    void hintsPerCode() {
        assertThat(hint(BackupErrorCode.PG_DUMP_NOT_FOUND)).contains("postgresql-client-17");
        assertThat(hint(BackupErrorCode.PG_DUMP_FAILED)).contains("major version");
        assertThat(hint(BackupErrorCode.NOT_CONFIGURED)).contains("BACKUP_FOLDER_ID");
        assertThat(hint(BackupErrorCode.DRIVE_FOLDER_NOT_FOUND)).contains("cannot access it");
        assertThat(hint(BackupErrorCode.ALREADY_RUNNING)).contains("See the server log around this time.");
    }

    private String hint(BackupErrorCode code) {
        MailService m = mock(MailService.class);
        new BackupAlertNotifier(m, props, "s@example.com", "p")
                .failure(BackupTier.DAILY, new BackupException(code, BackupStep.CONFIG, "x"), null);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(m).sendSimpleMail(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    @Test
    void unknownLastGoodWhenNull() {
        notifier.failure(BackupTier.DAILY, new BackupException(BackupErrorCode.PG_DUMP_FAILED,
                BackupStep.DUMP, "x"), null);
        assertThat(capture()[2]).contains("Last good backup: unknown");
    }

    @Test
    void pruneWarningSubject() {
        notifier.pruneWarning(BackupTier.MONTHLY, new BackupException(BackupErrorCode.PRUNE_FAILED,
                BackupStep.PRUNE, "boom"), "unknown");
        String[] m = capture();
        assertThat(m[1]).isEqualTo("[Funny Movies] DB backup WARNING (monthly): PRUNE_FAILED");
        assertThat(m[2]).contains("Step: PRUNE");
    }

    @Test
    void recipientIsAlertEmailElseMailUsername() {
        notifier.failure(BackupTier.DAILY, new BackupException(BackupErrorCode.INTERNAL_ERROR,
                BackupStep.DUMP, "x"), null);
        assertThat(capture()[0]).isEqualTo("sender@example.com");

        MailService m2 = mock(MailService.class);
        props.getBackup().setAlertEmail("ops@example.com");
        new BackupAlertNotifier(m2, props, "sender@example.com", "p").failure(BackupTier.DAILY,
                new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP, "x"), null);
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(m2).sendSimpleMail(to.capture(), anyString(), anyString());
        assertThat(to.getValue()).isEqualTo("ops@example.com");
    }

    @Test
    void noRecipientSendsNothing() {
        new BackupAlertNotifier(mail, props, "", "p").failure(BackupTier.DAILY,
                new BackupException(BackupErrorCode.INTERNAL_ERROR, BackupStep.DUMP, "x"), null);
        verify(mail, never()).sendSimpleMail(anyString(), anyString(), anyString());
    }

    @Test
    void secretsRedactedAndReasonTruncated() {
        String reason = "pw=db-pass cs=cs-secret rt=rt-secret " + "x".repeat(900);
        notifier.failure(BackupTier.DAILY, new BackupException(BackupErrorCode.PG_DUMP_FAILED,
                BackupStep.DUMP, reason), null);
        String body = capture()[2];
        assertThat(body).doesNotContain("db-pass", "cs-secret", "rt-secret").contains("pw=*** cs=*** rt=***");
        assertThat(body.length()).isLessThan(1000);
    }

    @Test
    void mailFailureIsSwallowed() {
        doThrow(new IllegalStateException("smtp down")).when(mail).sendSimpleMail(anyString(), anyString(), anyString());
        notifier.failure(BackupTier.DAILY, new BackupException(BackupErrorCode.INTERNAL_ERROR,
                BackupStep.DUMP, "x"), null);
        verify(mail).sendSimpleMail(anyString(), anyString(), anyString());
    }
}
