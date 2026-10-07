package com.canhlabs.funnyapp.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Public capabilities for the login form (GET /user/auth-options). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthOptionsDto {
    private boolean passwordLoginAvailable;
}
