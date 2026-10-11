package com.canhlabs.funnyapp.exception;

import java.io.IOException;

/** Not enough free disk to download a video and still keep the configured reserve. */
public class InsufficientDiskSpaceException extends IOException {
    public InsufficientDiskSpaceException(String message) {
        super(message);
    }
}
