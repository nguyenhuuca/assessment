package com.canhlabs.funnyapp.exception;

import com.canhlabs.funnyapp.dto.webapi.ResultErrorInfo;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CustomException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -2885187154886758927L;
    private final transient Object error;
    private final String message;
    /** Optional i18n key (also exposed to clients as {@code code}); when null, {@code message} is used as-is. */
    private final String messageKey;
    private final transient Object[] args;
    private final HttpStatus status;
    private final int subCode;
    private final String timestamp;

    public ResultErrorInfo buildErrorMessage() {
        return ResultErrorInfo.builder()
                .message(this.message)
                .code(this.messageKey)
                .error(this.error)
                .status(this.status.value())
                .subCode(this.subCode)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Builds a user-facing error identified by an i18n message key. The key doubles as the fallback
     * {@code message} so logs and un-localized contexts stay readable.
     */
    public static CustomException of(HttpStatus status, int subCode, String messageKey, Object... args) {
        return CustomException.builder()
                .status(status)
                .subCode(subCode)
                .message(messageKey)
                .messageKey(messageKey)
                .args(args)
                .build();
    }

    /** Throws a 400-by-default error identified by an i18n message key (see {@link #of}). */
    public static void raiseKey(String messageKey, Object... args) {
        throw CustomException.builder().message(messageKey).messageKey(messageKey).args(args).build();
    }

    public static void raiseErr(String msg) {
        throw CustomException.builder().message(msg).build();
    }

    public static void raiseErr(List<String> errors) {
        String errorsStr = String.join(";",errors);
        throw CustomException.builder().message(errorsStr).build();
    }
}
