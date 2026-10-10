package com.canhlabs.funnyapp.config;

import com.canhlabs.funnyapp.service.DriveUploader;
import com.canhlabs.funnyapp.service.impl.NotConfiguredDriveUploader;
import com.canhlabs.funnyapp.service.impl.OAuthDriveUploader;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class VideoImportConfigTest {

    private final VideoImportConfig config = new VideoImportConfig();

    @Test
    void noOauthProps_wiresNotConfiguredUploader() {
        DriveUploader uploader = config.driveUploader(new AppProperties());

        assertThat(uploader).isInstanceOf(NotConfiguredDriveUploader.class);
        assertThat(uploader.isConfigured()).isFalse();
    }

    @Test
    void partialOauthProps_stillNotConfigured() {
        AppProperties props = new AppProperties();
        props.getGoogleOauth().setClientId("id");
        props.getGoogleOauth().setClientSecret("  ");
        props.getGoogleOauth().setRefreshToken("rt");

        assertThat(config.driveUploader(props)).isInstanceOf(NotConfiguredDriveUploader.class);
    }

    @Test
    void allOauthProps_wiresOAuthUploader_withoutBuildingClientEagerly() {
        AppProperties props = new AppProperties();
        props.getGoogleOauth().setClientId("id");
        props.getGoogleOauth().setClientSecret("secret");
        props.getGoogleOauth().setRefreshToken("rt");

        DriveUploader uploader = config.driveUploader(props);

        assertThat(uploader).isInstanceOf(OAuthDriveUploader.class);
        assertThat(uploader.isConfigured()).isTrue();
    }

    @Test
    void videoImportDefaults_matchAdr() {
        AppProperties.VideoImport d = new AppProperties().getVideoImport();

        assertThat(d.isEnabled()).isFalse();
        assertThat(d.getYtDlpPath()).isEqualTo("yt-dlp");
        assertThat(d.getWorkDir()).isEqualTo("/opt/data/imports");
        assertThat(d.getMaxConcurrent()).isEqualTo(1);
        assertThat(d.getMaxFileSize()).isEqualTo("1G");
        assertThat(d.getDownloadTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(d.getMetadataTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(d.getMinFreeDiskBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(d.getCookiesPath()).isEmpty();
        assertThat(d.getBulkMax()).isEqualTo(20);
    }
}
