package com.canhlabs.funnyapp.exception;

import com.canhlabs.funnyapp.config.JsonMappers;
import tools.jackson.databind.json.JsonMapper;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;

public class UnauthorizedException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private static final JsonMapper MAPPER = JsonMappers.create();

    @Getter
    private final int subCode;



    public UnauthorizedException(String message, int subCode) {
        super(message);
        this.subCode = subCode;
    }


    public String toJson() {
        Error apiError = new Error(HttpStatus.valueOf(HttpStatus.UNAUTHORIZED.value()));
        apiError.setMessage(this.getMessage());
        apiError.setSubCode(subCode);
        HashMap<String, Object> errorWrapper = new HashMap<>();
        errorWrapper.put("status", "FAILED");
        errorWrapper.put("error", apiError);
        return MAPPER.writeValueAsString(errorWrapper);

    }
}
