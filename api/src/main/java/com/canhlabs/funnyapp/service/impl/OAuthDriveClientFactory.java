package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Builds the OAuth (refresh token) Drive client once, lazily, and shares it. Secrets are never logged. */
public class OAuthDriveClientFactory implements Supplier<Drive> {

    private final Supplier<Drive> builder;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Drive drive;

    public OAuthDriveClientFactory(String clientId, String clientSecret, String refreshToken) {
        this(() -> buildDrive(clientId, clientSecret, refreshToken));
    }

    /** Test seam. */
    OAuthDriveClientFactory(Supplier<Drive> builder) {
        this.builder = builder;
    }

    @Override
    public Drive get() {
        Drive d = drive;
        if (d == null) {
            lock.lock();
            try {
                d = drive;
                if (d == null) {
                    d = builder.get();
                    drive = d;
                }
            } finally {
                lock.unlock();
            }
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
