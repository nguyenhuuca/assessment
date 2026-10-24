package com.canhlabs.funnyapp.exception;

import com.canhlabs.funnyapp.enums.HlsErrorCode;
import lombok.Getter;

import java.io.Serial;

/** Failure of an HLS probe or transcode step; carries the BR-9 code that ends up in {@code hls_error}. */
@Getter
public class HlsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 7312045598120364417L;

    private final HlsErrorCode errorCode;

    public HlsException(HlsErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public HlsException(HlsErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }
}
