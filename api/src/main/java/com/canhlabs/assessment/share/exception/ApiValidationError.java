package com.canhlabs.assessment.share.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
class ApiValidationError extends ApiSubError {
    private String field;
    private Object rejectedValue;

    ApiValidationError(String object, String message) {
        this.object = object;
        this.message = message;
    }
    ApiValidationError(String object, String field, Object rejectedValue, String message) {
        this(object,message);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }
}
