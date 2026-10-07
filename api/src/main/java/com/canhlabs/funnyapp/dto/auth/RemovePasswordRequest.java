package com.canhlabs.funnyapp.dto.auth;

import com.canhlabs.funnyapp.aop.Sensitive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of DELETE /user/password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemovePasswordRequest {
    @Sensitive
    private String currentPassword;
    @Sensitive
    private String otp;
}
