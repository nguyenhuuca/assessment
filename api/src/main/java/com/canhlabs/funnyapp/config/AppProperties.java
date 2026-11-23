package com.canhlabs.funnyapp.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Using to load all the properties when start application
 * Can autowire this class in class that register to Spring Application Context
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties("app")
@Slf4j
public class AppProperties {

    private Long tokenExpired;
    private String jwtSecretKey;
    private String googleApiKey;
    // query condition
    private String googlePart;
    private String gptKey;
    /**
     * @deprecated no longer drives behaviour; replaced by {@code app.auth.password-login-enabled}.
     * Kept (nullable) only to detect the old key and log a deprecation warning.
     */
    @Deprecated
    private Boolean usePasswordless;
    private String domain;
    private String inviteTemplate;
    private String chatGptUrl;
    private String youtubeUrl;
    private String googleCredentialPath;
    private String imageUrl;
    private String mainDomain;
    private String imageStoragePath;
    private boolean subscriptionStatusEnabled;
    private EmailSetting emailSetting = new EmailSetting();
    private Auth auth = new Auth();
    private VideoImport videoImport = new VideoImport();
    private GoogleOauth googleOauth = new GoogleOauth();
    private Github github = new Github();
    private Payment payment = new Payment();
    private VideoSuggestion videoSuggestion = new VideoSuggestion();
    private WatchHistory watchHistory = new WatchHistory();
    private Hls hls = new Hls();
    private Backup backup = new Backup();

    @PostConstruct
    void warnDeprecatedKeys() {
        if (usePasswordless != null) {
            log.warn("'app.use-password-less' is deprecated and ignored; use 'app.auth.password-login-enabled' (magic link is always enabled)");
        }
    }

    /** Kill switch for the password endpoints only; magic link login is always on. */
    public boolean isPasswordLoginEnabled() {
        return auth != null && auth.isPasswordLoginEnabled();
    }

    /** Admin video import (ADR-0019): {@code app.video-import.*}. */
    @Getter
    @Setter
    public static class VideoImport {
        private boolean enabled = false;
        private String ytDlpPath = "yt-dlp";
        private String workDir = "/opt/data/imports";
        private int maxConcurrent = 1;
        private String maxFileSize = "1G";
        private Duration downloadTimeout = Duration.ofMinutes(30);
        private Duration metadataTimeout = Duration.ofSeconds(60);
        private long minFreeDiskBytes = 2L * 1024 * 1024 * 1024;
        private String cookiesPath = "";
        private int bulkMax = 20;
    }

    /** Database backup to Google Drive: {@code app.backup.*}. */
    @Getter
    @Setter
    public static class Backup {
        private boolean enabled = false;
        private String folderId = "";
        private String pgDumpPath = "pg_dump";
        private String workDir = "/opt/data/backups";
        private Duration timeout = Duration.ofMinutes(10);
        private String alertEmail = "";
        private Retention retention = new Retention();

        /** Number of backups kept per tier. */
        @Getter
        @Setter
        public static class Retention {
            private int daily = 7;
            private int weekly = 4;
            private int monthly = 12;
        }
    }

    /** User video suggestions: {@code app.video-suggestion.*}. */
    @Getter
    @Setter
    public static class VideoSuggestion {
        /** Max suggestions per user in any rolling 24 hours. */
        private int dailyLimit = 5;
    }

    /** Watch history (ADR-0021): {@code app.watch-history.*}. */
    @Getter
    @Setter
    public static class WatchHistory {
        /** Entries kept per user; the oldest are evicted on write. */
        private int maxEntries = 500;
    }

    /** HLS transcoding (ADR-0031): {@code app.hls.*}. */
    @Getter
    @Setter
    public static class Hls {
        /** Free space that must remain on the video-cache store after the source size (BR-6). */
        private long minFreeDiskBytes = 1024L * 1024 * 1024;
        /** Longest a single ffmpeg run may take before it is killed (BR-7). */
        private Duration timeout = Duration.ofMinutes(10);
    }

    /** OAuth user credential of the Drive folder owner, used only for uploads (ADR-0019 D2). */
    @Getter
    @Setter
    public static class GoogleOauth {
        private String clientId = "";
        private String clientSecret = "";
        private String refreshToken = "";

        public boolean isConfigured() {
            return notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /**
     * Bank-transfer payments (ADR-0037, ADR-0038): {@code app.payment.*}. The receiving account and business rules are
     * vendor-neutral; each provider adapter has its own nested block ({@code app.payment.sepay.*}).
     */
    @Getter
    @Setter
    public static class Payment {
        /** Provider id used for new orders; every configured provider still accepts webhooks. */
        private String provider = "sepay";
        private String bankAccount = "";
        private String bankCode = "";
        private String accountName = "";
        private String codePrefix = "FM";
        private int orderTtlHours = 24;
        private long minDonation = 10000;
        private Sepay sepay = new Sepay();

        /** Orders can only be created once the receiving bank account is known. */
        public boolean isOrderingConfigured() {
            return bankAccount != null && !bankAccount.isBlank() && bankCode != null && !bankCode.isBlank();
        }
    }

    /** SePay adapter settings: {@code app.payment.sepay.*}. Secrets default empty = webhook / API off. */
    @Getter
    @Setter
    public static class Sepay {
        private String webhookSecret = "";
        private String apiToken = "";
        /** SePay user API host: live https://userapi.sepay.vn, sandbox https://userapi-sandbox.sepay.vn. */
        private String apiBaseUrl = "https://userapi.sepay.vn";
        private String qrBaseUrl = "https://qr.sepay.vn/img";
        /** Comma-separated webhook source IPs; empty disables the allowlist. */
        private String allowedIps = "";

        public boolean isWebhookConfigured() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }

        public boolean isApiConfigured() {
            return apiToken != null && !apiToken.isBlank();
        }
    }

    /** GitHub issue creation for in-app reports: {@code app.github.*}. */
    @Getter
    @Setter
    public static class Github {
        private String token = "";
        private String owner = "nguyenhuuca";
        private String repo = "assessment";
        /** Shared secret of the repo webhook (ADR-0026 D7); blank turns the endpoint off. */
        private String webhookSecret = "";

        public boolean isConfigured() {
            return token != null && !token.isBlank();
        }

        public boolean isWebhookConfigured() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Auth {
        private boolean passwordLoginEnabled = true;
    }


    @Getter
    @Setter
    public static class EmailSetting {
        private boolean enablePreviewMode;
        private int percentage;
        private int maxDailyEmails;
        private String whitelist; // CSV format from env or YAML

        public List<String> getWhitelistAsList() {
            if (whitelist == null || whitelist.isBlank()) {
                return List.of();
            }
            return Arrays.stream(whitelist.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

}

