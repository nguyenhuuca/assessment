package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.exception.CustomException;

public final class Contract {

    private Contract() {
    }

    /**
     * Precondition check — throws IllegalArgumentException if condition is false.
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            CustomException.raiseErr(message);
        }
    }

    /**
     * Postcondition check — throws IllegalStateException if condition is false.
     */
    public static void ensure(boolean condition, String message) {
        if (!condition) {
            CustomException.raiseErr(message);
        }
    }

    /**
     * Invariant check — throws IllegalStateException if condition is false.
     */
    public static void invariant(boolean condition, String message) {
        if (!condition) {
            CustomException.raiseErr(message);
        }
    }

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
}