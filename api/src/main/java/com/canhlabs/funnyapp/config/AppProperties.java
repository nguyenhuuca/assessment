package com.canhlabs.funnyapp.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

