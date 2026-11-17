package com.canhlabs.funnyapp.service.backup;

/** Pipeline step in which a backup failure happened. */
public enum BackupStep { CONFIG, DUMP, UPLOAD, PRUNE }
