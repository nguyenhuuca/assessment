package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.service.backup.BackupFile;
import com.canhlabs.funnyapp.service.backup.BackupTier;

import java.nio.file.Path;
import java.util.List;

/** Remote storage for database backups, organised per tier. */
public interface BackupStore {

    /** Uploads the file into the tier location and returns the remote file id. */
    String upload(Path file, String name, BackupTier tier);

    /** Lists the backups of a tier, newest first. */
    List<BackupFile> list(BackupTier tier);

    /** Moves a remote file to the trash (recoverable). */
    void trash(String fileId);
}
