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
    public DriveUploader driveUploader(AppProperties props) {
        AppProperties.GoogleOauth oauth = props.getGoogleOauth();
        if (oauth != null && oauth.isConfigured()) {
            return new OAuthDriveUploader(oauth.getClientId(), oauth.getClientSecret(), oauth.getRefreshToken());
        }
        log.warn("GOOGLE_OAUTH_* not configured: video imports will fail with DRIVE_NOT_CONFIGURED");
        return new NotConfiguredDriveUploader();
    }
}
