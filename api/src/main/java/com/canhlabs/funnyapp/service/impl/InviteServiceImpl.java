package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.UserEmailRequest;
import com.canhlabs.funnyapp.repo.UserEmailRequestRepository;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.InviteService;
import com.canhlabs.funnyapp.service.MailService;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.enums.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static com.canhlabs.funnyapp.share.exception.CustomException.raiseErr;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private final UserEmailRequestRepository requestRepo;
    private final MailService emailSender;
    private final AppProperties appProperties;
    /**
     * Send link join system
     */
    @Override
    public void inviteUser(String email, Long invitedByUserId) {
        if (!AppUtils.isValidEmail(email)) {
           raiseErr("Invalid email");
        }
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
        String magicLink = appProperties.getDomain() + "/join?token=" + token;

        emailSender.sendInvitation(email, email, magicLink);
    }

    /**
     * Authenticate token from magic link
     */
    @Override
    public Optional<UserEmailRequest> verifyToken(String token) {
        return requestRepo.findByToken(token)
                .filter(req -> req.getStatus() == Status.PENDING)
                .filter(req -> req.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Mart token was used
     */
    @Override
    public void markTokenAsUsed(UserEmailRequest request, Long userId) {
        request.setStatus(Status.USED);
        request.setUsedAt(Instant.now());
        request.setUserId(userId);
        requestRepo.save(request);
    }
}
