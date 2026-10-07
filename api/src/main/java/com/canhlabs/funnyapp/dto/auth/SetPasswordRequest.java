package com.canhlabs.funnyapp.dto.auth;

import com.canhlabs.funnyapp.aop.Sensitive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of PUT /user/password. currentPassword is required only when the account already has a password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetPasswordRequest {
    @Sensitive
    private String currentPassword;
    @Sensitive
    private String newPassword;
    @Sensitive
    private String otp;
}
