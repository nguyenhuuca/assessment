package com.canhlabs.funnyapp.config.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})  // 👈 Cho cả method và class
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String value() default "";
}