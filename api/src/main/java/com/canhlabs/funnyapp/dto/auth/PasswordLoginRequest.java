package com.canhlabs.funnyapp.dto.auth;

import com.canhlabs.funnyapp.aop.Sensitive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

/** Body of POST /user/login. Both fields are masked in audit logs. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordLoginRequest {
    @Sensitive
    private String email;
    @Sensitive
    private String password;

    public String getEmail() {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
