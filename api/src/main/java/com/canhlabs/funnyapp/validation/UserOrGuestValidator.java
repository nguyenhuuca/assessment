package com.canhlabs.funnyapp.validation;

import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UserOrGuestValidator implements ConstraintValidator<UserOrGuest, CreateCommentRequest> {
    @Override
    public boolean isValid(CreateCommentRequest value, ConstraintValidatorContext context) {
        if (value == null) return false;
        // Unused since author identity moved to JWT / X-Guest-Token — safe to delete with @UserOrGuest
        return true;
    }
}
