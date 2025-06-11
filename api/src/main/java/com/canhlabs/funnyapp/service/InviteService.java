package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.domain.UserEmailRequest;

import java.util.Optional;

public interface InviteService {
    /**
     * Send link join system
     */
    void inviteUser(String email, Long invitedByUserId);

    /**
     * Authenticate token from magic link
     */
    Optional<UserEmailRequest> verifyToken(String token);

    /**
     * Mart token was used
     */
    void markTokenAsUsed(UserEmailRequest request, Long userId);
}
