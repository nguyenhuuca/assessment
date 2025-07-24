package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.MFASessionStore;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.UserEmailRequest;
import com.canhlabs.funnyapp.dto.JwtGenerationDto;
import com.canhlabs.funnyapp.dto.LoginDto;
import com.canhlabs.funnyapp.dto.MfaRequest;
import com.canhlabs.funnyapp.dto.SetupResponse;
import com.canhlabs.funnyapp.dto.TokenDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.UserInfoDto;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.QrUtil;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.utils.totp.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Mock
    private UserRepo userRepo;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private MFASessionStore mfaSessionStore;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private InviteServiceImpl inviteService;
    @Mock
    private Totp totp;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService.injectData(userRepo);
        userService.injectJwt(jwtProvider);
        userService.injectAuth(authenticationManager);
        userService.injectMfaStore(mfaSessionStore);
        userService.injectBCrypt(passwordEncoder);
        userService.injectInvite(inviteService);
        userService.injectTotp(totp);
    }

    @Test
    void joinSystem_registersNewUser() {
        LoginDto loginDto = LoginDto.builder().email("test@abc.com").password("pass").build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(null);
        User savedUser = User.builder().id(1L).userName("test@abc.com").password("encoded").build();
        when(userRepo.save(any())).thenReturn(savedUser);
        when(jwtProvider.generateToken(any(JwtGenerationDto.class))).thenReturn(TokenDto.builder().token("jwt").build());
        when(passwordEncoder.encode("pass")).thenReturn("encoded");

        UserInfoDto result = userService.joinSystem(loginDto);

        assertThat(result.getJwt()).isEqualTo("jwt");
    }

    @Test
    void joinSystem_logsInExistingUser() {
        LoginDto loginDto = LoginDto.builder().email("test@abc.com").password("pass").build();
        User user = User.builder().id(1L).userName("test@abc.com").password("encoded").build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);
        when(jwtProvider.generateToken(any(JwtGenerationDto.class))).thenReturn(TokenDto.builder().token("jwt").build());

        UserInfoDto result = userService.joinSystem(loginDto);

        assertThat(result.getJwt()).isEqualTo("jwt");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void joinSystem_invalidEmail_throwsException() {
        LoginDto loginDto = LoginDto.builder().email("invalid").password("pass").build();

        assertThatThrownBy(() -> userService.joinSystem(loginDto))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid email");
    }

    @Test
    void joinSystem_mfaRequired_returnsMfaRequired() {
        LoginDto loginDto = LoginDto.builder().email("test@abc.com").password("pass").build();
        User user = User.builder().id(1L).userName("test@abc.com").password("encoded").mfaEnabled(true).build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        UserInfoDto result = userService.joinSystem(loginDto);

        assertThat(result.getAction()).isEqualTo("MFA_REQUIRED");
        assertThat(result.getSessionToken()).isNotNull();
        verify(mfaSessionStore).storeSession(anyString(), eq("test@abc.com"));
    }

    @Test
    void loadUserByUsername_success() {
        User user = User.builder().userName("test@abc.com").password("pass").build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        var details = userService.loadUserByUsername("test@abc.com");

        assertThat(details.getUsername()).isEqualTo("test@abc.com");
        assertThat(details.getPassword()).isEqualTo("pass");
    }

    @Test
    void loadUserByUsername_noPassword_throws() {
        User user = User.builder().userName("test@abc.com").build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        assertThatThrownBy(() -> userService.loadUserByUsername("test@abc.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("test@abc.com");
    }

    @Test
    void generateSecret_returnsBase32String() {
        String secret = userService.generateSecret();
        assertThat(secret).isNotEmpty();
        // Should be base32, no '='
        assertThat(secret).doesNotContain("=");
    }

    @Test
    void enableMfa_success() {
        String userName = "test@abc.com";
        String secret = "SECRET";
        String otp = "123456";
        User user = User.builder().userName(userName).build();
        when(userRepo.findAllByUserName(userName)).thenReturn(user);
        when(userRepo.save(any())).thenReturn(user);
        when(totp.verify( otp, secret)).thenReturn(true);

        String result = userService.enableMfa(userName, secret, otp);

        assertThat(result).isEqualTo("success");
        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getMfaSecret()).isEqualTo(secret);

    }

    @Test
    void enableMfa_invalidOtp_throws() {
        String userName = "test@abc.com";
        String secret = "SECRET";
        String otp = "123456";
        User user = User.builder().userName(userName).build();
        when(userRepo.findAllByUserName(userName)).thenReturn(user);


        when(totp.verify(secret, otp)).thenReturn(false);

        assertThatThrownBy(() -> userService.enableMfa(userName, secret, otp))
                .isInstanceOf(CustomException.class)
                .hasMessage("Otp is incorrectly");

    }

    @Test
    void setupMfa_returnsSetupResponse() {
        String userName = "test@abc.com";
        try (MockedStatic<QrUtil> qrUtil = mockStatic(QrUtil.class)) {
            qrUtil.when(() -> QrUtil.generateQRCodeBase64(anyString(), anyInt(), anyInt()))
                    .thenReturn("qrCodeBase64");

            SetupResponse resp = userService.setupMfa(userName);

            assertThat(resp.secret()).isNotEmpty();
            assertThat(resp.qrCode()).isEqualTo("qrCodeBase64");
        }
    }

    @Test
    void verifyMfa_success() {
        String sessionToken = UUID.randomUUID().toString();
        String otp = "123456";
        String mfaSecret = "SECRET";
        User user = User.builder().userName("test@abc.com").mfaSecret(mfaSecret).build();
        MfaRequest req = new MfaRequest(sessionToken, otp, sessionToken);

        when(mfaSessionStore.getUserId(sessionToken)).thenReturn(Optional.of("test@abc.com"));
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);
        when(jwtProvider.generateToken(any(JwtGenerationDto.class))).thenReturn(TokenDto.builder().token("jwt").build());
        when(totp.verify(otp, mfaSecret)).thenReturn(true);

        UserInfoDto result = userService.verifyMfa(req);

        assertThat(result.getJwt()).isEqualTo("jwt");

    }

    @Test
    void verifyMfa_invalidSession_throws() {
        MfaRequest req = new MfaRequest("invalid", "otp", "test");
        when(mfaSessionStore.getUserId("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyMfa(req))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid or expired session");
    }

    @Test
    void verifyMfa_invalidOtp_throws() {
        String sessionToken = UUID.randomUUID().toString();
        String otp = "123456";
        String mfaSecret = "SECRET";
        User user = User.builder().userName("test@abc.com").mfaSecret(mfaSecret).build();
        MfaRequest req = new MfaRequest(sessionToken, otp, sessionToken);

        when(mfaSessionStore.getUserId(sessionToken)).thenReturn(Optional.of("test@abc.com"));
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        when(totp.verify(mfaSecret, otp)).thenReturn(false);

        assertThatThrownBy(() -> userService.verifyMfa(req))
                .isInstanceOf(CustomException.class)
                .hasMessage("Otp is incorrectly");

    }

    @Test
    void joinSystemPaswordless_existingUser() {
        String token = "token";
        UserEmailRequest req = UserEmailRequest.builder().email("test@abc.com").build();
        when(inviteService.verifyToken(token)).thenReturn(Optional.of(req));
        User user = User.builder().id(1L).userName("test@abc.com").build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);
        when(jwtProvider.generateToken(any(JwtGenerationDto.class))).thenReturn(TokenDto.builder().token("jwt").build());

        UserInfoDto result = userService.joinSystemPaswordless(token);

        assertThat(result.getJwt()).isEqualTo("jwt");
    }

    @Test
    void joinSystemPaswordless_newUser() {
        String token = "token";
        UserEmailRequest req = UserEmailRequest.builder().email("test@abc.com").userId(2L).build();
        when(inviteService.verifyToken(token)).thenReturn(Optional.of(req));
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(null);
        User newUser = User.builder().id(2L).userName("test@abc.com").build();
        when(userRepo.save(any())).thenReturn(newUser);
        when(jwtProvider.generateToken(any(JwtGenerationDto.class))).thenReturn(TokenDto.builder().token("jwt").build());

        UserInfoDto result = userService.joinSystemPaswordless(token);

        assertThat(result.getJwt()).isEqualTo("jwt");
        verify(inviteService).markTokenAsUsed(req, 2L);
    }

    @Test
    void joinSystemPaswordless_invalidToken_throws() {
        when(inviteService.verifyToken("badtoken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.joinSystemPaswordless("badtoken"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Token in invalid");
    }

    @Test
    void joinSystemPaswordless_mfaRequired() {
        String token = "token";
        UserEmailRequest req = UserEmailRequest.builder().email("test@abc.com").build();
        when(inviteService.verifyToken(token)).thenReturn(Optional.of(req));
        User user = User.builder().id(1L).userName("test@abc.com").mfaEnabled(true).build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        UserInfoDto result = userService.joinSystemPaswordless(token);

        assertThat(result.getAction()).isEqualTo("MFA_REQUIRED");
        assertThat(result.getSessionToken()).isNotNull();
        verify(mfaSessionStore).storeSession(anyString(), eq("test@abc.com"));
    }

    @Test
    void disableMfa_success() {
        String userName = "test@abc.com";
        String otp = "123456";
        String mfaSecret = "SECRET";
        User user = User.builder().userName(userName).mfaEnabled(true).mfaSecret(mfaSecret).build();
        when(userRepo.findAllByUserName(userName)).thenReturn(user);
        when(userRepo.save(any())).thenReturn(user);

        when(totp.verify(otp, mfaSecret)).thenReturn(true);

        String result = userService.disableMfa(userName, otp);

        assertThat(result).isEqualTo("success");
        assertThat(user.isMfaEnabled()).isFalse();

    }

    @Test
    void disableMfa_userNotExist_throws() {
        when(userRepo.findAllByUserName("notfound")).thenReturn(null);

        assertThatThrownBy(() -> userService.disableMfa("notfound", "otp"))
                .isInstanceOf(CustomException.class)
                .hasMessage("User not exist");
    }

    @Test
    void disableMfa_alreadyDisabled_throws() {
        User user = User.builder().userName("test@abc.com").mfaEnabled(false).build();
        when(userRepo.findAllByUserName("test@abc.com")).thenReturn(user);

        assertThatThrownBy(() -> userService.disableMfa("test@abc.com", "otp"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Mfa already disabled");
    }

    @Test
    void disableMfa_invalidOtp_throws() {
        String userName = "test@abc.com";
        String otp = "123456";
        String mfaSecret = "SECRET";
        User user = User.builder().userName(userName).mfaEnabled(true).mfaSecret(mfaSecret).build();
        when(userRepo.findAllByUserName(userName)).thenReturn(user);
        when(totp.verify(mfaSecret, otp)).thenReturn(true);
        assertThatThrownBy(() -> userService.disableMfa(userName, otp))
                .isInstanceOf(CustomException.class)
                .hasMessage("Otp is invalid!");

    }

    @Test
    void getCurrent_returnsCurrentUserDetail() {
        UserDetailDto mockUser = UserDetailDto.builder().id(1L).email("test@abc.com").build();

        try (MockedStatic<AppUtils> appUtils = mockStatic(AppUtils.class)) {
            appUtils.when(AppUtils::getCurrentUser).thenReturn(mockUser);

            UserDetailDto result = userService.getCurrent();

            assertThat(result).isEqualTo(mockUser);
        }
    }
}