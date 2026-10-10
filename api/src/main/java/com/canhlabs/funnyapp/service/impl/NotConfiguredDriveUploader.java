package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.client.CancelToken;
import com.canhlabs.funnyapp.exception.ImportErrorCode;
import com.canhlabs.funnyapp.exception.ImportException;
import com.canhlabs.funnyapp.service.DriveUploader;

import java.nio.file.Path;

/** Wired when no OAuth refresh token is configured, so the app starts and jobs fail with a clear code. */
public class NotConfiguredDriveUploader implements DriveUploader {
    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String upload(Path file, String fileName, String importJobId, ProgressListener listener, CancelToken cancel) {
        throw new ImportException(ImportErrorCode.DRIVE_NOT_CONFIGURED);
    }
}
