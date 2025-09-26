package com.canhlabs.funnyapp.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = UserOrGuestValidator.class)
public @interface UserOrGuest {
    String message() default "Either userId or guestName must be provided";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
