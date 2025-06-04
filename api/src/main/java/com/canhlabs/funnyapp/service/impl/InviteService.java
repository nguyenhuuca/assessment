package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.UserEmailRequest;
import com.canhlabs.funnyapp.repo.UserEmailRequestRepository;
import com.canhlabs.funnyapp.share.enums.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final UserEmailRequestRepository requestRepo;
    private final MailService emailSender; // bạn sẽ define riêng

    /**
     * Send link join system
     */
    public void inviteUser(String email, Long invitedByUserId) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        UserEmailRequest request = UserEmailRequest.builder()
                .email(email)
                .token(token)
                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                .status(Status.PENDING)
                .invitedByUserId(invitedByUserId)
                .purpose("JOIN_SYSTEM")
                .build();

        requestRepo.save(request);

        String frontendUrl = "https://canh-labs.com/join";
        String magicLink = frontendUrl + "?token=" + token;

        emailSender.sendInvitation(email, email, magicLink);
    }

    /**
     * Authenticate token from magic link
     */
    public Optional<UserEmailRequest> verifyToken(String token) {
        return requestRepo.findByToken(token)
                .filter(req -> req.getStatus() == Status.PENDING)
                .filter(req -> req.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Mart token was used
     */
    public void markTokenAsUsed(UserEmailRequest request, Long userId) {
        request.setStatus(Status.USED);
        request.setUsedAt(Instant.now());
        request.setUserId(userId);
        requestRepo.save(request);
    }
}
