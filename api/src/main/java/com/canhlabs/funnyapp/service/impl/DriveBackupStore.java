package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.BackupStore;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupFile;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Stores DB backups in per-tier subfolders of the configured Drive folder. Secrets are never logged. */
@Slf4j
@Service
public class DriveBackupStore implements BackupStore {

    static final int CHUNK_SIZE = 8 * 1024 * 1024;
    static final String APP_KEY = "backupApp";
    static final String APP_VALUE = "funnyapp-db";
    static final String TIER_KEY = "backupTier";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String FILE_MIME = "application/octet-stream";
    private static final Pattern FOLDER_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final Supplier<Drive> driveSupplier;
    private final AppProperties props;
    private final Map<BackupTier, String> tierFolders = new ConcurrentHashMap<>();

    @Autowired
    public DriveBackupStore(OAuthDriveClientFactory factory, AppProperties props) {
        this((Supplier<Drive>) factory, props);
    }

    /** Test seam. */
    DriveBackupStore(Supplier<Drive> driveSupplier, AppProperties props) {
        this.driveSupplier = driveSupplier;
        this.props = props;
    }

    @Override
    public String upload(Path file, String name, BackupTier tier) {
        for (int attempt = 0; ; attempt++) {
            String parent = tierFolder(tier);
            try {
                return send(file, name, tier, parent);
            } catch (IOException e) {
                if (attempt == 0 && isNotFound(e)) {
                    log.warn("Backup tier folder {} not found, re-resolving once", tier.id());
                    tierFolders.remove(tier);
                    continue;
                }
                throw uploadFailure(e);
            }
        }
    }

    @Override
    public List<BackupFile> list(BackupTier tier) {
        try {
            String parent = tierFolder(tier);
            String q = "'" + parent + "' in parents and trashed = false"
                    + " and appProperties has { key='" + APP_KEY + "' and value='" + APP_VALUE + "' }"
                    + " and appProperties has { key='" + TIER_KEY + "' and value='" + tier.id() + "' }";
            List<BackupFile> result = new ArrayList<>();
            String pageToken = null;
            do {
                FileList page = client().files().list()
                        .setQ(q)
                        .setOrderBy("createdTime desc")
                        .setFields("nextPageToken, files(id, name, createdTime)")
                        .setPageToken(pageToken)
                        .execute();
                if (page.getFiles() != null) {
                    for (File f : page.getFiles()) {
                        result.add(new BackupFile(f.getId(), f.getName(), toInstant(f)));
                    }
                }
                pageToken = page.getNextPageToken();
            } while (pageToken != null);
            return result;
        } catch (BackupException e) {
            throw new BackupException(BackupErrorCode.PRUNE_FAILED, BackupStep.PRUNE, e.getSafeReason(), e);
        } catch (IOException e) {
            throw pruneFailure("list", e);
        }
    }

    @Override
    public void trash(String fileId) {
        try {
            client().files().update(fileId, new File().setTrashed(true)).execute();
        } catch (IOException e) {
            throw pruneFailure("trash", e);
        }
    }

    private String send(Path file, String name, BackupTier tier, String parent) throws IOException {
        long size = Files.size(file);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            File metadata = new File()
                    .setName(name)
                    .setParents(List.of(parent))
                    .setMimeType(FILE_MIME)
                    .setAppProperties(Map.of(APP_KEY, APP_VALUE, TIER_KEY, tier.id()));
            InputStreamContent content = new InputStreamContent(FILE_MIME, in);
            content.setLength(size);
            Drive.Files.Create request = client().files().create(metadata, content).setFields("id");
            MediaHttpUploader uploader = request.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);
            uploader.setChunkSize(CHUNK_SIZE);
            File created = request.execute();
            if (created == null || created.getId() == null) {
                throw new BackupException(BackupErrorCode.DRIVE_UPLOAD_FAILED, BackupStep.UPLOAD,
                        "Drive returned no file id");
            }
            return created.getId();
        }
    }

    private String tierFolder(BackupTier tier) {
        String cached = tierFolders.get(tier);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveTierFolder(tier);
        tierFolders.put(tier, resolved);
        return resolved;
    }

    private String resolveTierFolder(BackupTier tier) {
        String root = rootFolderId();
        try {
            String q = "'" + root + "' in parents and name = '" + tier.id() + "' and mimeType = '"
                    + FOLDER_MIME + "' and trashed = false";
            FileList found = client().files().list()
                    .setQ(q)
                    .setOrderBy("createdTime")
                    .setFields("files(id)")
                    .execute();
            if (found.getFiles() != null && !found.getFiles().isEmpty()) {
                return found.getFiles().get(0).getId();
            }
            File folder = new File()
                    .setName(tier.id())
                    .setMimeType(FOLDER_MIME)
                    .setParents(List.of(root))
                    .setAppProperties(Map.of(APP_KEY, APP_VALUE));
            File created = client().files().create(folder).setFields("id").execute();
            if (created == null || created.getId() == null) {
                throw new BackupException(BackupErrorCode.DRIVE_UPLOAD_FAILED, BackupStep.UPLOAD,
                        "Drive returned no folder id");
            }
            return created.getId();
        } catch (IOException e) {
            if (isNotFound(e)) {
                throw new BackupException(BackupErrorCode.DRIVE_FOLDER_NOT_FOUND, BackupStep.UPLOAD,
                        "Drive backup folder not found or not shared with the OAuth account", e);
            }
            throw uploadFailure(e);
        }
    }

    private String rootFolderId() {
        String id = props.getBackup().getFolderId();
        if (id == null || !FOLDER_ID.matcher(id).matches()) {
            throw new BackupException(BackupErrorCode.NOT_CONFIGURED, BackupStep.CONFIG,
                    "app.backup.folder-id is missing or invalid");
        }
        return id;
    }

    private static Instant toInstant(File f) {
        return f.getCreatedTime() == null ? null : Instant.ofEpochMilli(f.getCreatedTime().getValue());
    }

    private static boolean isNotFound(IOException e) {
        return e instanceof GoogleJsonResponseException g && g.getStatusCode() == 404;
    }

    private static BackupException uploadFailure(IOException e) {
        boolean auth = isAuthError(e);
        log.warn("Drive backup upload failed ({})", auth ? "authorization" : e.getClass().getSimpleName());
        return auth
                ? new BackupException(BackupErrorCode.DRIVE_AUTH_FAILED, BackupStep.UPLOAD,
                        "Drive authorization failed; the OAuth refresh token may need renewal", e)
                : new BackupException(BackupErrorCode.DRIVE_UPLOAD_FAILED, BackupStep.UPLOAD,
                        "Drive upload failed (" + e.getClass().getSimpleName() + ")", e);
    }

    private static BackupException pruneFailure(String action, IOException e) {
        log.warn("Drive backup {} failed ({})", action, e.getClass().getSimpleName());
        return new BackupException(BackupErrorCode.PRUNE_FAILED, BackupStep.PRUNE,
                "Drive " + action + " failed (" + e.getClass().getSimpleName() + ")", e);
    }

    private static boolean isAuthError(IOException e) {
        if (e instanceof GoogleJsonResponseException g && g.getStatusCode() == 401) {
            return true;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("invalid_grant") || msg.contains("invalid_client") || msg.contains("unauthorized_client");
    }

    private Drive client() {
        return driveSupplier.get();
    }
}
