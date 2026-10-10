package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.CancelToken;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.service.DriveUploader;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthDriveUploaderTest {

    @TempDir
    Path tmp;

    private Drive drive;
    private Drive.Files files;
    private Drive.Files.Create create;
    private MediaHttpUploader mediaUploader;
    private Path video;

    @BeforeEach
    void setUp() throws IOException {
        drive = mock(Drive.class);
        files = mock(Drive.Files.class);
        create = mock(Drive.Files.Create.class);
        mediaUploader = mock(MediaHttpUploader.class);
        when(drive.files()).thenReturn(files);
        when(files.create(any(File.class), any(AbstractInputStreamContent.class))).thenReturn(create);
        when(create.setFields("id")).thenReturn(create);
        when(create.getMediaHttpUploader()).thenReturn(mediaUploader);
        video = tmp.resolve("video.mp4");
        Files.write(video, new byte[1000]);
    }

    private OAuthDriveUploader uploader() {
        return new OAuthDriveUploader(() -> drive);
    }

    @Test
    void upload_setsMetadataChunkingAndReturnsFileId() throws IOException {
        when(create.execute()).thenReturn(new File().setId("drive-123"));

        String id = uploader().upload(video, "My Video.mp4", "42", null, new CancelToken());

        assertThat(id).isEqualTo("drive-123");
        ArgumentCaptor<File> meta = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<AbstractInputStreamContent> content = ArgumentCaptor.forClass(AbstractInputStreamContent.class);
        verify(files).create(meta.capture(), content.capture());
        assertThat(meta.getValue().getName()).isEqualTo("My Video.mp4");
        assertThat(meta.getValue().getParents()).containsExactly(AppConstant.FOLDER_ID);
        assertThat(meta.getValue().getAppProperties()).containsEntry("importJobId", "42");
        assertThat(content.getValue().getType()).isEqualTo("video/mp4");
        assertThat(content.getValue().getLength()).isEqualTo(1000L);
        verify(mediaUploader).setDirectUploadEnabled(false);
        verify(mediaUploader).setChunkSize(8 * 1024 * 1024);
    }

    @Test
    void upload_forwardsProgressOnlyWhileInProgress() throws IOException {
        ArgumentCaptor<MediaHttpUploaderProgressListener> captor =
                ArgumentCaptor.forClass(MediaHttpUploaderProgressListener.class);
        when(create.execute()).thenAnswer(inv -> {
            verify(mediaUploader).setProgressListener(captor.capture());
            MediaHttpUploader progress = mock(MediaHttpUploader.class);
            when(progress.getUploadState()).thenReturn(MediaHttpUploader.UploadState.INITIATION_STARTED,
                    MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS);
            when(progress.getNumBytesUploaded()).thenReturn(400L);
            captor.getValue().progressChanged(progress);
            captor.getValue().progressChanged(progress);
            return new File().setId("x");
        });
        List<long[]> seen = new ArrayList<>();

        uploader().upload(video, "a.mp4", "1", (up, total) -> seen.add(new long[]{up, total}), new CancelToken());

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)).containsExactly(400, 1000);
    }

    @Test
    void upload_cancelledDuringProgress_abortsAsCancelled() throws IOException {
        CancelToken token = new CancelToken();
        ArgumentCaptor<MediaHttpUploaderProgressListener> captor =
                ArgumentCaptor.forClass(MediaHttpUploaderProgressListener.class);
        when(create.execute()).thenAnswer(inv -> {
            verify(mediaUploader).setProgressListener(captor.capture());
            token.cancel();
            captor.getValue().progressChanged(mock(MediaHttpUploader.class));
            return new File().setId("x");
        });

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, token))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.CANCELLED));
    }

    @Test
    void upload_quotaError_isUploadFailed_withoutLeakingDetail() throws IOException {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders());
        when(create.execute()).thenThrow(new GoogleJsonResponseException(builder, null));

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_UPLOAD_FAILED);
                    assertThat(e.getMessage()).doesNotContain("403");
                });
    }

    @Test
    void upload_unauthorizedHttp_isAuthFailed() throws IOException {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(401, "Unauthorized", new HttpHeaders());
        when(create.execute()).thenThrow(new GoogleJsonResponseException(builder, null));

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_AUTH_FAILED));
    }

    @Test
    void upload_invalidGrant_isAuthFailed() throws IOException {
        when(create.execute()).thenThrow(new IOException("400 Bad Request {\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_AUTH_FAILED);
                    assertThat(e.getMessage()).doesNotContain("invalid_grant");
                });
    }

    @Test
    void upload_genericIoError_isUploadFailed() throws IOException {
        when(create.execute()).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_UPLOAD_FAILED));
    }

    @Test
    void upload_responseWithoutId_isUploadFailed() throws IOException {
        when(create.execute()).thenReturn(new File());

        assertThatThrownBy(() -> uploader().upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_UPLOAD_FAILED));
    }

    @Test
    void upload_missingFile_isInternalError() {
        assertThatThrownBy(() -> uploader().upload(tmp.resolve("nope.mp4"), "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.INTERNAL_ERROR));
    }

    @Test
    void driveClient_isBuiltOnceAndReused() throws IOException {
        when(create.execute()).thenReturn(new File().setId("x"));
        int[] builds = {0};
        OAuthDriveUploader u = new OAuthDriveUploader(() -> {
            builds[0]++;
            return drive;
        });

        u.upload(video, "a.mp4", "1", null, new CancelToken());
        u.upload(video, "b.mp4", "2", null, new CancelToken());

        assertThat(builds[0]).isEqualTo(1);
        assertThat(u.isConfigured()).isTrue();
    }

    @Test
    void notConfiguredUploader_failsWithDriveNotConfigured() {
        DriveUploader none = new NotConfiguredDriveUploader();

        assertThat(none.isConfigured()).isFalse();
        assertThatThrownBy(() -> none.upload(video, "a.mp4", "1", null, new CancelToken()))
                .isInstanceOfSatisfying(ImportException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImportErrorCode.DRIVE_NOT_CONFIGURED));
    }
}
