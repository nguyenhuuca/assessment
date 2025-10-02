package com.canhlabs.funnyapp.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Custom annotation to mark methods or classes for audit logging.
 * Can be applied at both method and class levels.
 */
@Target({ElementType.METHOD, ElementType.TYPE})  // 👈 Both method and class
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String value() default "";
}