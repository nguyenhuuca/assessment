package com.canhlabs.funnyapp.dto.usersettings;

import com.canhlabs.funnyapp.aop.Sensitive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for DELETE /user/account.
 * MFA users supply otp; non-MFA users supply confirmation (their email).
 * Both fields are masked in audit logs via @Sensitive and the masking.fields config.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequest {
    @Sensitive
    private String otp;
    @Sensitive
    private String confirmation;
}
