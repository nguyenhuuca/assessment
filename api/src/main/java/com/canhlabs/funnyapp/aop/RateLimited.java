package com.canhlabs.funnyapp.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark methods for rate limiting.
 * Can be applied at method level.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    int permit() default 10;        // max number of calls
    int windowInSeconds() default 60; // time window in seconds
}