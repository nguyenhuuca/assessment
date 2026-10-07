package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.auth.PasswordLoginRequest;
import com.canhlabs.funnyapp.dto.auth.RemovePasswordRequest;
import com.canhlabs.funnyapp.dto.auth.SetPasswordRequest;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;

/** Optional email + password login (ADR-0018). */
public interface PasswordLoginService {

    /**
     * Verifies email + password. Every failure (unknown email, no password, wrong password, deactivated, locked)
     * raises the same 401 INVALID_CREDENTIALS.
     *
     * @return jwt, or action MFA_REQUIRED + session token for MFA users
     */
    UserInfoDto login(PasswordLoginRequest request);

    /** Sets or changes the password of the current user; returns a fresh JWT for the current session. */
    UserInfoDto setPassword(SetPasswordRequest request);

    /** Removes the password of the current user (back to magic link only); returns a fresh JWT for the current session. */
    UserInfoDto removePassword(RemovePasswordRequest request);
}
