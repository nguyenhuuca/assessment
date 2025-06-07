package com.canhlabs.funnyapp.share.exception;

import com.canhlabs.funnyapp.share.ResultErrorInfo;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class CustomException extends RuntimeException {
    private static final long serialVersionUID = -2885187154886758927L;
    private final transient Object error;
    private final String message;
    private final HttpStatus status;
    private final int subCode;
    private final String timestamp;

    public ResultErrorInfo buildErrorMessage() {
        return ResultErrorInfo.builder()
                .message(this.message)
                .error(this.error)
                .status(this.status.value())
                .subCode(this.subCode)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static void raiseErr(String msg) {
        throw CustomException.builder().message(msg).build();
    }
}
