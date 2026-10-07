package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.CredentialsVersionCache;
import com.canhlabs.funnyapp.cache.EmailCacheLimiter;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.auth.PasswordLoginRequest;
import com.canhlabs.funnyapp.dto.auth.RemovePasswordRequest;
import com.canhlabs.funnyapp.dto.auth.SetPasswordRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.LoginAttemptService;
import com.canhlabs.funnyapp.service.MailService;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.totp.Totp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordLoginServiceImplTest {

    private static final String EMAIL = "alice@example.com";
    private static final String GOOD_PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "another-long-secret-1";

    @Mock
    private UserRepo userRepo;
    @Mock
    private UserService userService;
    @Mock
    private LoginAttemptService loginAttempts;
    @Mock
    private CredentialsVersionCache credentialsVersionCache;
    @Mock
    private Totp totp;
    @Mock
    private MailService mailService;
    @Mock
    private EmailCacheLimiter emailLimiter;
    @Mock
    private AppProperties appProperties;

    /** Plain encoder for building fixtures: calling the spy inside when(...) would corrupt Mockito stubbing. */
    private final BCryptPasswordEncoder fixtureEncoder = new BCryptPasswordEncoder(4);
    private BCryptPasswordEncoder encoder;
    private PasswordLoginServiceImpl service;
    private MockedStatic<AppUtils> appUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        encoder = Mockito.spy(new BCryptPasswordEncoder(4));
        when(appProperties.isPasswordLoginEnabled()).thenReturn(true);
        AppProperties.EmailSetting emailSetting = new AppProperties.EmailSetting();
        emailSetting.setMaxDailyEmails(200);
        when(appProperties.getEmailSetting()).thenReturn(emailSetting);
        service = new PasswordLoginServiceImpl(userRepo, encoder, userService, loginAttempts,
                credentialsVersionCache, totp, mailService, emailLimiter, appProperties);
        Mockito.clearInvocations(encoder); // ignore the dummy-hash encode done in the constructor
        when(userService.completeLogin(any(User.class))).thenAnswer(inv ->
                UserInfoDto.builder().jwt("jwt-login").build());
        when(userService.issueToken(any(User.class))).thenReturn("jwt-fresh");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (appUtils != null) {
            appUtils.close();
        }
    }

    private User userWithPassword() {
        return User.builder().id(1L).userName(EMAIL).password(fixtureEncoder.encode(GOOD_PASSWORD)).build();
    }

    private void currentUser(User user) {
        appUtils = Mockito.mockStatic(AppUtils.class);
        appUtils.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().id(user.getId()).email(user.getUserName()).build());
        when(userRepo.findAllById(user.getId())).thenReturn(user);
        Mockito.clearInvocations(encoder);
    }

    private static PasswordLoginRequest login(String email, String password) {
        return new PasswordLoginRequest(email, password);
    }

    private static CustomException thrown(Runnable r) {
        try {
            r.run();
        } catch (CustomException e) {
            return e;
        }
        throw new AssertionError("CustomException expected");
    }

    // ------------------------------------------------------------------ login

    @Test
    void login_success_returnsCompleteLoginResult_andDoesNotTouchCounters() {
        User user = userWithPassword();
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        UserInfoDto result = service.login(login("  Alice@Example.com ", GOOD_PASSWORD));

        assertThat(result.getJwt()).isEqualTo("jwt-login");
        verify(loginAttempts, never()).recordFailure(anyLong());
        verify(loginAttempts, never()).recordSuccess(anyLong());
    }

    @Test
    void login_success_resetsPreviousFailures() {
        User user = userWithPassword();
        user.setFailedLoginCount(3);
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        service.login(login(EMAIL, GOOD_PASSWORD));

        verify(loginAttempts).recordSuccess(1L);
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void login_success_afterLockExpired_clearsStaleLock() {
        User user = userWithPassword();
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        service.login(login(EMAIL, GOOD_PASSWORD));

        verify(loginAttempts).recordSuccess(1L);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void login_mfaUser_delegatesToCompleteLogin() {
        User user = userWithPassword();
        user.setMfaEnabled(true);
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);
        when(userService.completeLogin(user)).thenReturn(
                UserInfoDto.builder().action("MFA_REQUIRED").sessionToken("s").build());

        UserInfoDto result = service.login(login(EMAIL, GOOD_PASSWORD));

        assertThat(result.getAction()).isEqualTo("MFA_REQUIRED");
        assertThat(result.getSessionToken()).isEqualTo("s");
        assertThat(result.getJwt()).isNull();
    }

    @Test
    void login_wrongPassword_recordsFailure_and401() {
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(userWithPassword());

        CustomException e = thrown(() -> service.login(login(EMAIL, "wrong-password-1")));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(loginAttempts).recordFailure(1L);
        verify(userService, never()).completeLogin(any());
    }

    @Test
    void login_failureIsRecordedBeforeTheExceptionPropagates() {
        // recordFailure runs in its own REQUIRES_NEW transaction, so it is committed even though login throws
        List<String> order = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            order.add("recordFailure");
            return null;
        }).when(loginAttempts).recordFailure(1L);
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(userWithPassword());

        assertThatThrownBy(() -> service.login(login(EMAIL, "wrong-password-1"))).isInstanceOf(CustomException.class);
        order.add("thrown");

        assertThat(order).containsExactly("recordFailure", "thrown");
    }

    @Test
    void login_unknownEmail_runsDummyMatch_andDoesNotCount() {
        when(userRepo.findAllByUserName("nobody@example.com")).thenReturn(null);

        CustomException e = thrown(() -> service.login(login("nobody@example.com", GOOD_PASSWORD)));

        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(encoder).matches(eq(GOOD_PASSWORD), anyString());
        verify(loginAttempts, never()).recordFailure(anyLong());
    }

    @Test
    void login_userWithoutPassword_runsDummyMatch() {
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(User.builder().id(1L).userName(EMAIL).build());

        CustomException e = thrown(() -> service.login(login(EMAIL, GOOD_PASSWORD)));

        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(encoder).matches(eq(GOOD_PASSWORD), anyString());
        verify(loginAttempts, never()).recordFailure(anyLong());
    }

    @Test
    void login_deactivatedUser_isRejectedEvenWithCorrectPassword() {
        User user = userWithPassword();
        user.setStatus(UserStatus.DEACTIVATED);
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        CustomException e = thrown(() -> service.login(login(EMAIL, GOOD_PASSWORD)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(userService, never()).completeLogin(any());
        verify(encoder).matches(eq(GOOD_PASSWORD), anyString());
    }

    @Test
    void login_lockedUser_isRejectedEvenWithCorrectPassword_andDoesNotExtendLock() {
        User user = userWithPassword();
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        CustomException e = thrown(() -> service.login(login(EMAIL, GOOD_PASSWORD)));

        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(userService, never()).completeLogin(any());
        verify(loginAttempts, never()).recordFailure(anyLong());
        verify(encoder).matches(eq(GOOD_PASSWORD), anyString());
    }

    @Test
    void login_missingOrBlankFields_areInvalidCredentials() {
        assertThat(thrown(() -> service.login(login(null, GOOD_PASSWORD))).getMessage()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(thrown(() -> service.login(login("  ", GOOD_PASSWORD))).getMessage()).isEqualTo("INVALID_CREDENTIALS");
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(userWithPassword());
        assertThat(thrown(() -> service.login(login(EMAIL, null))).getMessage()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(thrown(() -> service.login(login(EMAIL, ""))).getMessage()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_passwordLongerThan72Bytes_isRejected_withoutTruncationMatch() {
        // first 72 bytes equal the real password's prefix: a truncating encoder would accept the longer string
        String base = "x".repeat(72);
        User user = User.builder().id(1L).userName(EMAIL).password(fixtureEncoder.encode(base)).build();
        when(userRepo.findAllByUserName(EMAIL)).thenReturn(user);

        CustomException e = thrown(() -> service.login(login(EMAIL, base + "tail")));

        assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
        verify(userService, never()).completeLogin(any());
    }

    @Test
    void login_everyFailureProducesTheSameErrorShape() {
        User locked = userWithPassword();
        locked.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        User deactivated = userWithPassword();
        deactivated.setStatus(UserStatus.DEACTIVATED);
        User noPassword = User.builder().id(1L).userName(EMAIL).build();

        List<CustomException> errors = new ArrayList<>();
        when(userRepo.findAllByUserName(anyString())).thenReturn(null);
        errors.add(thrown(() -> service.login(login("nobody@example.com", GOOD_PASSWORD))));
        for (User u : List.of(locked, deactivated, noPassword, userWithPassword())) {
            when(userRepo.findAllByUserName(EMAIL)).thenReturn(u);
            errors.add(thrown(() -> service.login(login(EMAIL, "wrong-password-1"))));
        }
        errors.add(thrown(() -> service.login(login(EMAIL, null))));

        assertThat(errors).hasSizeGreaterThan(5).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getSubCode()).isEqualTo(401);
            assertThat(e.getMessage()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(e.buildErrorMessage().getMessage()).isEqualTo("INVALID_CREDENTIALS");
        });
    }

    @Test
    void login_flagOff_is404_andNothingIsLookedUp() {
        when(appProperties.isPasswordLoginEnabled()).thenReturn(false);

        CustomException e = thrown(() -> service.login(login(EMAIL, GOOD_PASSWORD)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepo, never()).findAllByUserName(anyString());
    }

    // ------------------------------------------------------------ set password

    private SetPasswordRequest set(String current, String next, String otp) {
        return new SetPasswordRequest(current, next, otp);
    }

    @Test
    void setPassword_firstTime_savesHash_bumpsVersion_evictsCache_returnsFreshJwt_sendsEmail() {
        User user = User.builder().id(1L).userName(EMAIL).credentialsVersion(2).build();
        currentUser(user);

        UserInfoDto result = service.setPassword(set(null, NEW_PASSWORD, null));

        assertThat(result.getJwt()).isEqualTo("jwt-fresh");
        assertThat(result.getAction()).isEqualTo("PASSWORD_SET");
        assertThat(fixtureEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        assertThat(user.getPasswordSetAt()).isNotNull();
        assertThat(user.getCredentialsVersion()).isEqualTo(3);
        verify(userRepo).save(user);
        verify(credentialsVersionCache).evict(1L);
        // the token is issued after the version bump so it carries the new "cv"
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepo, credentialsVersionCache, userService);
        order.verify(userRepo).save(user);
        order.verify(credentialsVersionCache).evict(1L);
        order.verify(userService).issueToken(user);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendSimpleMail(eq(EMAIL), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Mật khẩu của bạn vừa được thay đổi");
        assertThat(body.getValue()).doesNotContain(NEW_PASSWORD).contains("đã bị đăng xuất");
        verify(emailLimiter).incrementDailyCount(any());
    }

    @Test
    void setPassword_change_requiresAndAcceptsCurrentPassword() {
        User user = userWithPassword();
        currentUser(user);

        UserInfoDto result = service.setPassword(set(GOOD_PASSWORD, NEW_PASSWORD, null));

        assertThat(result.getAction()).isEqualTo("PASSWORD_CHANGED");
        assertThat(fixtureEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
    }

    @Test
    void setPassword_change_missingCurrentPassword_is400_andNothingChanges() {
        User user = userWithPassword();
        String oldHash = user.getPassword();
        currentUser(user);

        CustomException e = thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(user.getPassword()).isEqualTo(oldHash);
        assertThat(user.getCredentialsVersion()).isZero();
        verify(userRepo, never()).save(any());
        verify(mailService, never()).sendSimpleMail(anyString(), anyString(), anyString());
    }

    @Test
    void setPassword_change_wrongCurrentPassword_is400_andCountsTowardLockout() {
        User user = userWithPassword();
        currentUser(user);

        CustomException e = thrown(() -> service.setPassword(set("not-the-password", NEW_PASSWORD, null)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).isEqualTo("CURRENT_PASSWORD_INVALID");
        verify(loginAttempts).recordFailure(1L);
        verify(userRepo, never()).save(any());
    }

    @Test
    void setPassword_change_whileLocked_isRejected_withoutComparingCurrentPassword() {
        User user = userWithPassword();
        user.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        currentUser(user);

        CustomException e = thrown(() -> service.setPassword(set(GOOD_PASSWORD, NEW_PASSWORD, null)));

        assertThat(e.getMessage()).isEqualTo("CURRENT_PASSWORD_INVALID");
        verify(encoder, never()).matches(eq(GOOD_PASSWORD), eq(user.getPassword()));
        verify(userRepo, never()).save(any());
    }

    @Test
    void setPassword_correctCurrentPassword_clearsEarlierFailures_andSavedEntityDoesNotRestoreThem() {
        User user = userWithPassword();
        user.setFailedLoginCount(3);
        currentUser(user);

        service.setPassword(set(GOOD_PASSWORD, NEW_PASSWORD, null));

        verify(loginAttempts).recordSuccess(1L);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginCount()).isZero();
    }

    @Test
    void setPassword_mfaUser_requiresValidOtp() {
        User user = User.builder().id(1L).userName(EMAIL).mfaEnabled(true).mfaSecret("SECRET").build();
        currentUser(user);
        when(totp.verify("111111", "SECRET")).thenReturn(false);
        when(totp.verify("123456", "SECRET")).thenReturn(true);

        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null))).getMessage()).isEqualTo("OTP_INVALID");
        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, ""))).getMessage()).isEqualTo("OTP_INVALID");
        CustomException wrong = thrown(() -> service.setPassword(set(null, NEW_PASSWORD, "111111")));
        assertThat(wrong.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrong.getMessage()).isEqualTo("OTP_INVALID");
        verify(userRepo, never()).save(any());

        UserInfoDto ok = service.setPassword(set(null, NEW_PASSWORD, "123456"));
        assertThat(ok.getJwt()).isEqualTo("jwt-fresh");
    }

    @Test
    void setPassword_nonMfaUser_otpIsIgnored() {
        currentUser(User.builder().id(1L).userName(EMAIL).build());

        service.setPassword(set(null, NEW_PASSWORD, null));

        verify(totp, never()).verify(anyString(), anyString());
    }

    @Test
    void setPassword_policyViolations_are400WithCode_andNothingChanges() {
        User user = User.builder().id(1L).userName(EMAIL).build();
        currentUser(user);

        assertThat(thrown(() -> service.setPassword(set(null, "short", null))).getMessage()).isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(thrown(() -> service.setPassword(set(null, "x7".repeat(37), null))).getMessage()).isEqualTo("PASSWORD_TOO_LONG");
        assertThat(thrown(() -> service.setPassword(set(null, "my-ALICE-secret", null))).getMessage()).isEqualTo("PASSWORD_CONTAINS_EMAIL");
        CustomException common = thrown(() -> service.setPassword(set(null, "password123", null)));
        assertThat(common.getMessage()).isEqualTo("PASSWORD_TOO_COMMON");
        assertThat(common.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepo, never()).save(any());
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void setPassword_emailFailure_isLoggedNotFatal() {
        currentUser(User.builder().id(1L).userName(EMAIL).build());
        doThrow(new RuntimeException("smtp down")).when(mailService).sendSimpleMail(anyString(), anyString(), anyString());

        UserInfoDto result = service.setPassword(set(null, NEW_PASSWORD, null));

        assertThat(result.getJwt()).isEqualTo("jwt-fresh");
        verify(emailLimiter, never()).incrementDailyCount(any());
    }

    @Test
    void setPassword_emailBudgetExhausted_securityMailIsStillSent() {
        currentUser(User.builder().id(1L).userName(EMAIL).build());
        when(emailLimiter.getDailyCount(any())).thenReturn(200);

        service.setPassword(set(null, NEW_PASSWORD, null));

        verify(mailService).sendSimpleMail(eq(EMAIL), anyString(), anyString());
        verify(emailLimiter).incrementDailyCount(any());
    }

    @Test
    void setPassword_flagOff_is404() {
        when(appProperties.isPasswordLoginEnabled()).thenReturn(false);

        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null))).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void setPassword_noAuthenticatedUser_is401() {
        appUtils = Mockito.mockStatic(AppUtils.class);
        appUtils.when(AppUtils::getCurrentUser).thenReturn(null);

        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null))).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void setPassword_userGoneFromDb_is401() {
        appUtils = Mockito.mockStatic(AppUtils.class);
        appUtils.when(AppUtils::getCurrentUser).thenReturn(UserDetailDto.builder().id(9L).build());
        when(userRepo.findAllById(9L)).thenReturn(null);

        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null))).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void setPassword_deactivatedUser_is403() {
        User user = User.builder().id(1L).userName(EMAIL).status(UserStatus.DEACTIVATED).build();
        currentUser(user);

        assertThat(thrown(() -> service.setPassword(set(null, NEW_PASSWORD, null))).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --------------------------------------------------------- remove password

    private RemovePasswordRequest remove(String current, String otp) {
        return new RemovePasswordRequest(current, otp);
    }

    @Test
    void removePassword_success_clearsHash_bumpsVersion_evicts_returnsJwt_sendsEmail() {
        User user = userWithPassword();
        user.setPasswordSetAt(Instant.now());
        currentUser(user);

        UserInfoDto result = service.removePassword(remove(GOOD_PASSWORD, null));

        assertThat(result.getJwt()).isEqualTo("jwt-fresh");
        assertThat(result.getAction()).isEqualTo("PASSWORD_REMOVED");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getPasswordSetAt()).isNull();
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
        verify(credentialsVersionCache).evict(1L);
        verify(mailService).sendSimpleMail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void removePassword_wrongCurrent_is400_countsFailure_keepsPassword() {
        User user = userWithPassword();
        currentUser(user);

        CustomException e = thrown(() -> service.removePassword(remove("nope-nope-nope", null)));

        assertThat(e.getMessage()).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(loginAttempts).recordFailure(1L);
        assertThat(user.getPassword()).isNotNull();
        verify(userRepo, never()).save(any());
    }

    @Test
    void removePassword_missingCurrent_is400() {
        currentUser(userWithPassword());

        assertThat(thrown(() -> service.removePassword(remove(null, null))).getMessage()).isEqualTo("CURRENT_PASSWORD_INVALID");
    }

    @Test
    void removePassword_noPasswordSet_is400() {
        currentUser(User.builder().id(1L).userName(EMAIL).build());

        CustomException e = thrown(() -> service.removePassword(remove("x", null)));

        assertThat(e.getMessage()).isEqualTo("PASSWORD_NOT_SET");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removePassword_mfaUser_requiresOtp() {
        User user = userWithPassword();
        user.setMfaEnabled(true);
        user.setMfaSecret("SECRET");
        currentUser(user);
        when(totp.verify("123456", "SECRET")).thenReturn(true);

        assertThat(thrown(() -> service.removePassword(remove(GOOD_PASSWORD, null))).getMessage()).isEqualTo("OTP_INVALID");
        assertThat(user.getPassword()).isNotNull();

        assertThat(service.removePassword(remove(GOOD_PASSWORD, "123456")).getJwt()).isEqualTo("jwt-fresh");
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void removePassword_flagOff_is404() {
        when(appProperties.isPasswordLoginEnabled()).thenReturn(false);

        assertThat(thrown(() -> service.removePassword(remove("x", null))).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
