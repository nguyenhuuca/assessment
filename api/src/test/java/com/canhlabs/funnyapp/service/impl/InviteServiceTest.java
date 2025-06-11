// File: api/src/test/java/com/canhlabs/funnyapp/service/impl/InviteServiceTest.java
package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.UserEmailRequest;
import com.canhlabs.funnyapp.repo.UserEmailRequestRepository;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.enums.Status;
import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteServiceTest {

    @Mock
    private UserEmailRequestRepository requestRepo;
    @Mock
    private MailService emailSender;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private InviteService inviteService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void inviteUser_sendsInvitation_whenEmailIsValid() {
        String email = "test@abc.com";
        Long invitedByUserId = 1L;
        when(appProperties.getDomain()).thenReturn("http://localhost");
        try (MockedStatic<AppUtils> utils = mockStatic(AppUtils.class)) {
            utils.when(() -> AppUtils.isValidEmail(email)).thenReturn(true);

            inviteService.inviteUser(email, invitedByUserId);

            verify(requestRepo).save(any(UserEmailRequest.class));
            verify(emailSender).sendInvitation(eq(email), eq(email), contains("/join/?token="));
        }
    }

    @Test
    void inviteUser_throwsException_whenEmailIsInvalid() {
        String email = "invalid-email";
        Long invitedByUserId = 1L;
        try (MockedStatic<AppUtils> utils = mockStatic(AppUtils.class)) {
            utils.when(() -> AppUtils.isValidEmail(email)).thenReturn(false);

            assertThatThrownBy(() -> inviteService.inviteUser(email, invitedByUserId))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Invalid email");
        }
    }

    @Test
    void verifyToken_returnsRequest_whenTokenIsValidAndPending() {
        String token = "valid-token";
        UserEmailRequest request = UserEmailRequest.builder()
                .token(token)
                .status(Status.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(requestRepo.findByToken(token)).thenReturn(Optional.of(request));

        Optional<UserEmailRequest> result = inviteService.verifyToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo(token);
    }

    @Test
    void verifyToken_returnsEmpty_whenTokenIsNotFound() {
        String token = "invalid-token";
        when(requestRepo.findByToken(token)).thenReturn(Optional.empty());

        Optional<UserEmailRequest> result = inviteService.verifyToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void verifyToken_returnsEmpty_whenTokenIsNotPending() {
        String token = "used-token";
        UserEmailRequest request = UserEmailRequest.builder()
                .token(token)
                .status(Status.USED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(requestRepo.findByToken(token)).thenReturn(Optional.of(request));

        Optional<UserEmailRequest> result = inviteService.verifyToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void verifyToken_returnsEmpty_whenTokenIsExpired() {
        String token = "expired-token";
        UserEmailRequest request = UserEmailRequest.builder()
                .token(token)
                .status(Status.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(requestRepo.findByToken(token)).thenReturn(Optional.of(request));

        Optional<UserEmailRequest> result = inviteService.verifyToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void markTokenAsUsed_updatesRequestWithUsedStatusAndUserId() {
        UserEmailRequest request = UserEmailRequest.builder()
                .status(Status.PENDING)
                .build();
        Long userId = 1L;

        inviteService.markTokenAsUsed(request, userId);

        assertThat(request.getStatus()).isEqualTo(Status.USED);
        assertThat(request.getUsedAt()).isNotNull();
        assertThat(request.getUserId()).isEqualTo(userId);
        verify(requestRepo).save(request);
    }

    @Test
    void markTokenAsUsed_doesNotThrow_whenRequestIsAlreadyUsed() {
        UserEmailRequest request = UserEmailRequest.builder()
                .status(Status.USED)
                .build();
        Long userId = 1L;

        inviteService.markTokenAsUsed(request, userId);

        assertThat(request.getStatus()).isEqualTo(Status.USED);
        assertThat(request.getUserId()).isEqualTo(userId);
        verify(requestRepo).save(request);
    }
}