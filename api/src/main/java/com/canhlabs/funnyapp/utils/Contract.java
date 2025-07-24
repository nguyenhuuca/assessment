package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;

import java.util.function.Predicate;

public final class Contract {

    private Contract() {}

    // Core checker
    private static void check(boolean condition, String message) {
        if (!condition) {
            CustomException.raiseErr(message);
        }
    }


    // ====== REQUIRE: Precondition ======
    public static void require(boolean condition, String message) {
        check(condition, message);
    }

    public static <T> T require(T value, Predicate<T> validator, String message) {
        require(validator.test(value), message);
        return value;
    }

    public static <T> T requireNonNull(T obj, String message) {
        require(obj != null, message);
        return obj;
    }

    public static String requireNotBlank(String str, String message) {
        require(str != null && !str.trim().isEmpty(), message);
        return str;
    }

    // ====== ENSURE: Postcondition ======
    public static void ensure(boolean condition, String message) {
        check(condition, message);
    }

    public static <T> T ensure(T value, Predicate<T> validator, String message) {
        ensure(validator.test(value), message);
        return value;
    }

    public static <T> T ensureNonNull(T obj, String message) {
        ensure(obj != null, message);
        return obj;
    }

    // ====== INVARIANT: Always True ======
    public static void invariant(boolean condition, String message) {
        check(condition, message);
    }

    public static <T> T invariant(T value, Predicate<T> validator, String message) {
        invariant(validator.test(value), message);
        return value;
    }
}