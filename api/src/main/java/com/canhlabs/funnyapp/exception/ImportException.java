package com.canhlabs.funnyapp.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * Failure of an import step. {@code message} is already sanitized and safe to store / show to the admin;
 * raw tool output never travels in this exception.
 */
@Getter
public class ImportException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 4421863299364523501L;

    private final String errorCode;

    public ImportException(String errorCode) {
        super(ImportErrorCode.messageFor(errorCode));
        this.errorCode = errorCode;
    }

    public ImportException(String errorCode, Throwable cause) {
        super(ImportErrorCode.messageFor(errorCode), cause);
        this.errorCode = errorCode;
    }
}
