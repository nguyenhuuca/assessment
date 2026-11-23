package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.DriveUploader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VideoImportConfig {

    /** OAuth uploader only when client id, secret and refresh token are all present; app starts either way. */
    @Bean
    public DriveUploader driveUploader(AppProperties props, OAuthDriveClientFactory driveClientFactory) {
        AppProperties.GoogleOauth oauth = props.getGoogleOauth();
        if (oauth != null && oauth.isConfigured()) {
            return new OAuthDriveUploader(driveClientFactory);
        }
        log.warn("GOOGLE_OAUTH_* not configured: video imports will fail with DRIVE_NOT_CONFIGURED");
        return new NotConfiguredDriveUploader();
    }

    /** Convenience for callers (tests) without a shared factory; not a bean method. */
    DriveUploader driveUploader(AppProperties props) {
        return driveUploader(props, oauthDriveClientFactory(props));
    }

    /** Shared lazy Drive client; building it does no network I/O, so it is safe when OAuth is not configured. */
    @Bean
    public OAuthDriveClientFactory oauthDriveClientFactory(AppProperties props) {
        AppProperties.GoogleOauth oauth = props.getGoogleOauth();
        if (oauth == null) {
            return new OAuthDriveClientFactory(null, null, null);
        }
        return new OAuthDriveClientFactory(oauth.getClientId(), oauth.getClientSecret(), oauth.getRefreshToken());
    }
}
