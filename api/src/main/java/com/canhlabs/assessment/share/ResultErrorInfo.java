package com.canhlabs.assessment.share;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * Hold the error info when return to client
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ResultErrorInfo implements Serializable {
    private static final long serialVersionUID = -6448620768602438445L;
    private transient Object error;
    private String message;
    private int status;
    private int subCode;
    private String timestamp;
}
