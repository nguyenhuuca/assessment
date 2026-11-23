package com.canhlabs.funnyapp.service.backup;

import java.time.Instant;

/** A backup file stored remotely. */
public record BackupFile(String id, String name, Instant createdTime) {
}
