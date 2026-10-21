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
    private VideoSuggestion videoSuggestion = new VideoSuggestion();
    private WatchHistory watchHistory = new WatchHistory();

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

