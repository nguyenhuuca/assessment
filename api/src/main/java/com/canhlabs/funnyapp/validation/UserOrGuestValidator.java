package com.canhlabs.funnyapp.validation;

import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UserOrGuestValidator implements ConstraintValidator<UserOrGuest, CreateCommentRequest> {
    @Override
    public boolean isValid(CreateCommentRequest value, ConstraintValidatorContext context) {
        if (value == null) return false;
        boolean hasUser = value.getUserId() != null && !value.getUserId().isBlank();
        boolean hasGuest = value.getGuestName() != null && !value.getGuestName().isBlank();
        return hasUser || hasGuest;
    }
}
