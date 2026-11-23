package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.MailService;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupRedactor;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Sends the one alert (failure) or warning (cleanup failed) email of a backup run. */
@Slf4j
@Component
public class BackupAlertNotifier {

    static final int REASON_MAX = 500;
    private static final String DEFAULT_HINT = "See the server log around this time.";
    private static final Map<BackupErrorCode, String> HINTS = Map.of(
            BackupErrorCode.DRIVE_AUTH_FAILED,
            "The Google OAuth refresh token has expired or was revoked. Issue a new one and update "
                    + "GOOGLE_OAUTH_REFRESH_TOKEN, then use Run now.",
            BackupErrorCode.PG_DUMP_NOT_FOUND,
            "Install postgresql-client-17 on the host or set BACKUP_PG_DUMP_PATH.",
            BackupErrorCode.PG_DUMP_FAILED,
            "Check that the pg_dump major version is >= the server's, and the DB credentials.",
            BackupErrorCode.NOT_CONFIGURED,
            "Set BACKUP_FOLDER_ID and the GOOGLE_OAUTH_* variables.",
            BackupErrorCode.DRIVE_FOLDER_NOT_FOUND,
            "The folder does not exist or the OAuth account cannot access it.");

    private final MailService mailService;
    private final AppProperties props;
    private final String mailUsername;
    private final String dbPassword;

    public BackupAlertNotifier(MailService mailService, AppProperties props,
                               @Value("${spring.mail.username:}") String mailUsername,
                               @Value("${spring.datasource.password:}") String dbPassword) {
        this.mailService = mailService;
        this.props = props;
        this.mailUsername = mailUsername;
        this.dbPassword = dbPassword;
    }

    public void failure(BackupTier tier, BackupException e, String lastGood) {
        send("[Funny Movies] DB backup FAILED (" + tier.id() + "): " + e.getCode(), tier, e, lastGood);
    }

    public void pruneWarning(BackupTier tier, BackupException e, String lastGood) {
        send("[Funny Movies] DB backup WARNING (" + tier.id() + "): PRUNE_FAILED", tier, e, lastGood);
    }

    private void send(String subject, BackupTier tier, BackupException e, String lastGood) {
        try {
            String to = recipient();
            if (to.isBlank()) {
                log.error("Backup alert email failed: no recipient configured");
                return;
            }
            mailService.sendSimpleMail(to, subject, body(tier, e, lastGood));
        } catch (RuntimeException ex) {
            log.error("Backup alert email failed ({})", ex.getClass().getSimpleName());
        }
    }

    private String recipient() {
        String configured = props.getBackup().getAlertEmail();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return mailUsername == null ? "" : mailUsername.trim();
    }

    private String body(BackupTier tier, BackupException e, String lastGood) {
        String reason = BackupRedactor.redact(e.getSafeReason(), REASON_MAX, secrets());
        return "Tier: " + tier.id() + "\n"
                + "Step: " + e.getStep() + "\n"
                + "Code: " + e.getCode() + "\n"
                + "Time (UTC): " + Instant.now() + "\n"
                + "Reason: " + reason + "\n"
                + "Hint: " + HINTS.getOrDefault(e.getCode(), DEFAULT_HINT) + "\n"
                + "Last good backup: " + (lastGood == null ? "unknown" : lastGood) + "\n";
    }

    private List<String> secrets() {
        AppProperties.GoogleOauth oauth = props.getGoogleOauth();
        return List.of(nz(dbPassword), nz(oauth.getClientSecret()), nz(oauth.getRefreshToken()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
