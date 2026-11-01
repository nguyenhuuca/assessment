package com.canhlabs.funnyapp.exception;

import java.io.Serial;

/**
 * A looked-up row does not exist. Extends {@link IllegalArgumentException} so single-row endpoints keep
 * their current 400 response; bulk actions catch this type to report the id under {@code notFound}.
 */
public class ResourceNotFoundException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 4021757395346263201L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
