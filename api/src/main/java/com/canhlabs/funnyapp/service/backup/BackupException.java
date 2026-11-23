package com.canhlabs.funnyapp.service.backup;

/** Backup failure carrying a code, the failing step and a reason safe to log or email. */
public class BackupException extends RuntimeException {
    private final BackupErrorCode code;
    private final BackupStep step;
    private final String safeReason;

    public BackupException(BackupErrorCode code, BackupStep step, String safeReason) {
        this(code, step, safeReason, null);
    }

    public BackupException(BackupErrorCode code, BackupStep step, String safeReason, Throwable cause) {
        super(code + " at " + step + ": " + safeReason, cause);
        this.code = code;
        this.step = step;
        this.safeReason = safeReason;
    }

    public BackupErrorCode getCode() {
        return code;
    }

    public BackupStep getStep() {
        return step;
    }

    public String getSafeReason() {
        return safeReason;
    }
}
