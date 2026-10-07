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
import com.canhlabs.funnyapp.service.PasswordLoginService;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.PasswordPolicy;
import com.canhlabs.funnyapp.utils.totp.Totp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static com.canhlabs.funnyapp.service.impl.Converter.toUserInfo;

/**
 * Optional email + password login (ADR-0018).
 * <p>
 * Deliberately NOT {@code @Transactional}: failure counters are persisted by {@link LoginAttemptService} in their
 * own transaction and every other write is a single repository call, so the CustomException thrown after a failed
 * attempt can never roll the counter back.
 */
@Slf4j
@Service
public class PasswordLoginServiceImpl implements PasswordLoginService {

    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";
    static final String OTP_INVALID = "OTP_INVALID";
    static final String PASSWORD_NOT_SET = "PASSWORD_NOT_SET";

    private static final String DUMMY_PASSWORD = "dummy-password-for-constant-time-check";

    private final UserRepo userRepo;
    private final PasswordEncoder bCrypt;
    private final UserService userService;
    private final LoginAttemptService loginAttempts;
    private final CredentialsVersionCache credentialsVersionCache;
    private final Totp totp;
    private final MailService mailService;
    private final EmailCacheLimiter emailLimiter;
    private final AppProperties appProperties;
    /** Hash of a throw-away password; matched against on paths that have no real hash so timing stays equal. */
    private final String dummyHash;

    public PasswordLoginServiceImpl(UserRepo userRepo, PasswordEncoder bCrypt, UserService userService,
                                    LoginAttemptService loginAttempts, CredentialsVersionCache credentialsVersionCache,
                                    Totp totp, MailService mailService, EmailCacheLimiter emailLimiter,
                                    AppProperties appProperties) {
        this.userRepo = userRepo;
        this.bCrypt = bCrypt;
        this.userService = userService;
        this.loginAttempts = loginAttempts;
        this.credentialsVersionCache = credentialsVersionCache;
        this.totp = totp;
        this.mailService = mailService;
        this.emailLimiter = emailLimiter;
        this.appProperties = appProperties;
        this.dummyHash = bCrypt.encode(DUMMY_PASSWORD);
    }

    // ------------------------------------------------------------------ login

    @Override
    public UserInfoDto login(PasswordLoginRequest request) {
        requireEnabled();
        String email = request.getEmail();
        String password = request.getPassword();
        User user = (email == null || email.isBlank()) ? null : userRepo.findAllByUserName(email);

        boolean usable = user != null
                && user.getPassword() != null
                && user.getStatus() != UserStatus.DEACTIVATED
                && !isLocked(user)
                && isCheckable(password);
        if (!usable) {
            burnTime(password);
            if (user != null && isLocked(user)) {
                log.warn("Password login rejected for locked account id={}", user.getId());
            }
            throw invalidCredentials();
        }

        if (!bCrypt.matches(password, user.getPassword())) {
            loginAttempts.recordFailure(user.getId());
            throw invalidCredentials();
        }
        clearFailures(user);
        return userService.completeLogin(user);
    }

    // ------------------------------------------------------------ set / remove

    @Override
    public UserInfoDto setPassword(SetPasswordRequest request) {
        requireEnabled();
        User user = currentUser();
        PasswordPolicy.validate(request.getNewPassword(), user.getUserName());
        boolean hadPassword = user.getPassword() != null;
        if (hadPassword) {
            verifyCurrentPassword(user, request.getCurrentPassword());
        }
        verifyOtpIfMfa(user, request.getOtp());

        user.setPassword(bCrypt.encode(request.getNewPassword()));
        user.setPasswordSetAt(Instant.now());
        UserInfoDto result = persistCredentialChange(user, hadPassword ? "PASSWORD_CHANGED" : "PASSWORD_SET");
        sendSecurityEmail(user, hadPassword ? "đã được thay đổi" : "đã được thiết lập");
        return result;
    }

    @Override
    public UserInfoDto removePassword(RemovePasswordRequest request) {
        requireEnabled();
        User user = currentUser();
        if (user.getPassword() == null) {
            throw error(HttpStatus.BAD_REQUEST, 400, PASSWORD_NOT_SET);
        }
        verifyCurrentPassword(user, request.getCurrentPassword());
        verifyOtpIfMfa(user, request.getOtp());

        user.setPassword(null);
        user.setPasswordSetAt(null);
        UserInfoDto result = persistCredentialChange(user, "PASSWORD_REMOVED");
        sendSecurityEmail(user, "đã được gỡ bỏ");
        return result;
    }

    // ----------------------------------------------------------------- helpers

    private void requireEnabled() {
        if (!appProperties.isPasswordLoginEnabled()) {
            throw error(HttpStatus.NOT_FOUND, 404, "Not Found");
        }
    }

    private User currentUser() {
        UserDetailDto current = AppUtils.getCurrentUser();
        User user = current == null || current.getId() == null ? null : userRepo.findAllById(current.getId());
        if (user == null) {
            throw error(HttpStatus.UNAUTHORIZED, 401, "TOKEN_INVALID");
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw error(HttpStatus.FORBIDDEN, 403, "Account deactivated");
        }
        return user;
    }

    /**
     * Wrong current password counts toward the lockout. While the account is locked the password is not even
     * compared (a locked account must not become a guessing oracle through this endpoint).
     */
    private void verifyCurrentPassword(User user, String currentPassword) {
        if (isLocked(user) || !isCheckable(currentPassword)) {
            burnTime(currentPassword);
            throw error(HttpStatus.BAD_REQUEST, 400, CURRENT_PASSWORD_INVALID);
        }
        if (!bCrypt.matches(currentPassword, user.getPassword())) {
            loginAttempts.recordFailure(user.getId());
            throw error(HttpStatus.BAD_REQUEST, 400, CURRENT_PASSWORD_INVALID);
        }
        clearFailures(user);
    }

    private void verifyOtpIfMfa(User user, String otp) {
        if (user.isMfaEnabled() && (otp == null || otp.isBlank() || !totp.verify(otp, user.getMfaSecret()))) {
            throw error(HttpStatus.BAD_REQUEST, 400, OTP_INVALID);
        }
    }

    /** Bumps credentials_version, saves, evicts the cached version and issues a JWT for the current session. */
    private UserInfoDto persistCredentialChange(User user, String action) {
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        User saved = userRepo.save(user);
        User persisted = saved != null ? saved : user;
        credentialsVersionCache.evict(persisted.getId());
        UserInfoDto info = toUserInfo(persisted, userService.issueToken(persisted), action);
        log.info("Credentials changed for user id={} action={}", persisted.getId(), action);
        return info;
    }

    /** Resets the persisted counters (own transaction) and the in-memory copy so a later save() cannot undo it. */
    private void clearFailures(User user) {
        if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
            loginAttempts.recordSuccess(user.getId());
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    /** BCrypt only uses the first 72 bytes; refuse longer input instead of silently truncating or burning CPU. */
    private boolean isCheckable(String password) {
        return password != null && !password.isEmpty()
                && password.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES;
    }

    private void burnTime(String password) {
        bCrypt.matches(isCheckable(password) ? password : DUMMY_PASSWORD, dummyHash);
    }

    private CustomException invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, 401, INVALID_CREDENTIALS);
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }

    /**
     * Plain-text security notice. Counts against the daily budget but is never blocked by it (nor by the
     * notification digest's 50% cap); a send failure is logged and never fails the request.
     */
    private void sendSecurityEmail(User user, String event) {
        try {
            LocalDate today = LocalDate.now();
            int max = appProperties.getEmailSetting() != null ? appProperties.getEmailSetting().getMaxDailyEmails() : 0;
            if (max > 0 && emailLimiter.getDailyCount(today) >= max) {
                log.warn("Daily email budget exhausted; sending security mail to user id={} anyway", user.getId());
            }
            mailService.sendSimpleMail(user.getUserName(),
                    "Mật khẩu của bạn vừa được thay đổi",
                    "Xin chào,\n\n"
                            + "Mật khẩu đăng nhập của tài khoản " + user.getUserName() + " " + event + ".\n"
                            + "Các thiết bị khác đã bị đăng xuất.\n\n"
                            + "Nếu bạn không thực hiện thay đổi này, hãy đăng nhập bằng link gửi qua email "
                            + "và đổi hoặc gỡ mật khẩu ngay.\n\n"
                            + "Canh Labs");
            emailLimiter.incrementDailyCount(today);
        } catch (Exception e) {
            log.error("Could not send password security email for user id={}: {}", user.getId(), e.getMessage());
        }
    }
}
