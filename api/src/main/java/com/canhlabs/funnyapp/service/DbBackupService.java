package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.service.backup.BackupTier;

/** Takes a database backup, stores it remotely and applies the retention of the tier. */
public interface DbBackupService {

    /**
     * Runs one backup of the given tier. Returns normally when disabled or when only the cleanup failed.
     *
     * @throws com.canhlabs.funnyapp.service.backup.BackupException when the run failed (an alert was already sent)
     */
    void run(BackupTier tier);
}
