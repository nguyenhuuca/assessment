// File: api/src/test/java/com/canhlabs/funnyapp/service/impl/InviteServiceTest.java
package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.UserEmailRequest;
import com.canhlabs.funnyapp.repo.UserEmailRequestRepository;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

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
}