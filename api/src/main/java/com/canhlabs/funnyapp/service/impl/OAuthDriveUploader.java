package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.CancelToken;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.service.DriveUploader;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Uploads with the folder owner OAuth refresh token because Service Accounts have no Drive quota
 * (ADR-0019 D2 PoC). Secrets are never logged.
 */
@Slf4j
public class OAuthDriveUploader implements DriveUploader {

    static final int CHUNK_SIZE = 8 * 1024 * 1024;
    private static final String MIME = "video/mp4";

    private final Supplier<Drive> driveSupplier;
    private volatile Drive drive;

    public OAuthDriveUploader(String clientId, String clientSecret, String refreshToken) {
        this(() -> buildDrive(clientId, clientSecret, refreshToken));
    }

    /** Test seam. */
    OAuthDriveUploader(Supplier<Drive> driveSupplier) {
        this.driveSupplier = driveSupplier;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String upload(Path file, String fileName, String importJobId, ProgressListener listener, CancelToken cancel) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new ImportException(ImportErrorCode.INTERNAL_ERROR, e);
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Drive client = client();
            File metadata = new File()
                    .setName(fileName)
                    .setParents(List.of(AppConstant.FOLDER_ID))
                    .setAppProperties(Map.of("importJobId", importJobId));
            InputStreamContent content = new InputStreamContent(MIME, in);
            content.setLength(size);

            Drive.Files.Create request = client.files().create(metadata, content).setFields("id");
            MediaHttpUploader uploader = request.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);
            uploader.setChunkSize(CHUNK_SIZE);
            uploader.setProgressListener(u -> {
                if (cancel != null && cancel.isCancelled()) {
                    throw new IOException("upload cancelled");
                }
                if (listener != null && u.getUploadState() == MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS) {
                    listener.onProgress(u.getNumBytesUploaded(), size);
                }
            });
            File created = request.execute();
            if (created == null || created.getId() == null) {
                throw new ImportException(ImportErrorCode.DRIVE_UPLOAD_FAILED);
            }
            return created.getId();
        } catch (ImportException e) {
            throw e;
        } catch (GoogleJsonResponseException e) {
            int status = e.getStatusCode();
            log.warn("Drive upload rejected: HTTP {}", status);
            throw new ImportException(status == 401 ? ImportErrorCode.DRIVE_AUTH_FAILED
                    : ImportErrorCode.DRIVE_UPLOAD_FAILED, e);
        } catch (IOException e) {
            if (cancel != null && cancel.isCancelled()) {
                throw new ImportException(ImportErrorCode.CANCELLED, e);
            }
            String msg = e.getMessage() == null ? "" : e.getMessage();
            boolean auth = msg.contains("invalid_grant") || msg.contains("invalid_client")
                    || msg.contains("unauthorized_client");
            log.warn("Drive upload failed ({})", auth ? "authorization" : e.getClass().getSimpleName());
            throw new ImportException(auth ? ImportErrorCode.DRIVE_AUTH_FAILED
                    : ImportErrorCode.DRIVE_UPLOAD_FAILED, e);
        }
    }

    private Drive client() {
        Drive d = drive;
        if (d == null) {
            d = driveSupplier.get();
            drive = d;
        }
        return d;
    }

    private static Drive buildDrive(String clientId, String clientSecret, String refreshToken) {
        try {
            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials.createScoped(List.of(DriveScopes.DRIVE))))
                    .setApplicationName("VideoStreamApp")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new ImportException(ImportErrorCode.DRIVE_AUTH_FAILED, e);
        }
    }
}
