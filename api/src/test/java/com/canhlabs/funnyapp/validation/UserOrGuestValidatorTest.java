package com.canhlabs.funnyapp.validation;

import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserOrGuestValidatorTest {

    private final UserOrGuestValidator validator = new UserOrGuestValidator();

    @Test
    void nullRequest_isInvalid() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void anyRequest_isValid_sinceIdentityComesFromTheToken() {
        assertThat(validator.isValid(new CreateCommentRequest(), null)).isTrue();
    }
}
