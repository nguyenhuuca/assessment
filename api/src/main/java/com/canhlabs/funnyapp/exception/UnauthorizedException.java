package com.canhlabs.funnyapp.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;

public class UnauthorizedException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Getter
    private final int subCode;



    public UnauthorizedException(String message, int subCode) {
        super(message);
        this.subCode = subCode;
    }


    public String toJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Error apiError = new Error(HttpStatus.valueOf(HttpStatus.UNAUTHORIZED.value()));
        apiError.setMessage(this.getMessage());
        apiError.setSubCode(subCode);
        HashMap<String, Object> errorWrapper = new HashMap<>();
        errorWrapper.put("status", "FAILED");
        errorWrapper.put("error", apiError);
        return mapper.writeValueAsString(errorWrapper);

    }
}
