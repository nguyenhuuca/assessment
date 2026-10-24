package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.IncognitoCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.usersettings.AccountStatusDto;
import com.canhlabs.funnyapp.dto.usersettings.DeleteAccountRequest;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.enums.DefaultQuality;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import com.canhlabs.funnyapp.repo.WatchHistoryRepository;
import com.canhlabs.funnyapp.service.UserSettingsService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.totp.Totp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("vi", "en");

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepo userRepo;
    private final AppProperties appProperties;
    private final Totp totp;
    private final WatchHistoryRepository watchHistoryRepository;
    private final IncognitoCache incognitoCache;

    @Override
    @Transactional
    public UserSettingsDto getSettings() {
        var currentUser = AppUtils.getCurrentUser();
        Long userId = currentUser.getId();
        String email = currentUser.getEmail();

        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        User user = userRepo.findAllById(userId);
        return buildDto(email, user, settings);
    }

    @Override
    @Transactional
    public UserSettingsDto updateSettings(UpdateUserSettingsRequest request) {
        // Validate atomically: check all fields before persisting anything
        boolean hasAnyField = request.getNotifyNewContent() != null
                || request.getNotifyEmail() != null
                || request.getDefaultQuality() != null
                || request.getIncognitoEnabled() != null
                || request.getProfilePrivate() != null
                || request.isLocaleProvided();

        if (!hasAnyField) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.settings.emptyUpdate");
        }

        if (request.getDefaultQuality() != null) {
            try {
                DefaultQuality.fromWireValue(request.getDefaultQuality());
            } catch (IllegalArgumentException e) {
                throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.settings.invalidQuality");
            }
        }

        if (request.getLocale() != null && !SUPPORTED_LOCALES.contains(request.getLocale())) {
            throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.settings.invalidLocale");
        }

        // All validations passed — now apply
        var currentUser = AppUtils.getCurrentUser();
        Long userId = currentUser.getId();
        String email = currentUser.getEmail();

        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        if (request.getNotifyNewContent() != null) {
            settings.setNotifyNewContent(request.getNotifyNewContent());
        }
        if (request.getNotifyEmail() != null) {
            settings.setNotifyEmail(request.getNotifyEmail());
        }
        if (request.getDefaultQuality() != null) {
            settings.setDefaultQuality(request.getDefaultQuality());
        }
        if (request.getIncognitoEnabled() != null) {
            settings.setIncognitoEnabled(request.getIncognitoEnabled());
        }
        if (request.getProfilePrivate() != null) {
            settings.setProfilePrivate(request.getProfilePrivate());
        }

        if (request.isLocaleProvided()) {
            settings.setLocale(request.getLocale());
        }

        settings = userSettingsRepository.save(settings);
        incognitoCache.evict(userId);

        User user = userRepo.findAllById(userId);
        return buildDto(email, user, settings);
    }

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        var currentUser = AppUtils.getCurrentUser();
        Long userId = currentUser.getId();

        User user = userRepo.findAllById(userId);

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw CustomException.of(HttpStatus.CONFLICT, 409, "error.account.alreadyDeactivated");
        }

        if (user.isMfaEnabled()) {
            boolean valid = totp.verify(request.getOtp(), user.getMfaSecret());
            if (!valid) {
                throw CustomException.of(HttpStatus.FORBIDDEN, 403, "error.otp.incorrect");
            }
        } else {
            if (request.getConfirmation() == null || !request.getConfirmation().equals(user.getUserName())) {
                throw CustomException.of(HttpStatus.BAD_REQUEST, 400, "error.account.confirmationMismatch");
            }
        }

        // Step-up verified — soft-delete and anonymize
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeletedAt(Instant.now());
        user.setUserName("deleted_user_" + user.getId());
        user.setPassword(null);
        user.setMfaSecret(null);
        user.setMfaEnabled(false);
        userRepo.save(user);
        watchHistoryRepository.deleteAll(userId);
        incognitoCache.evict(userId);
        log.info("Account deactivated and anonymized for user id={}", userId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UserSettings createDefaultSettings(Long userId) {
        UserSettings defaults = new UserSettings();
        defaults.setUserId(userId);
        defaults.setNotifyNewContent(true);
        defaults.setNotifyEmail(true);
        defaults.setDefaultQuality("AUTO");
        defaults.setIncognitoEnabled(false);
        defaults.setProfilePrivate(false);
        UserSettings saved = userSettingsRepository.save(defaults);
        incognitoCache.evict(userId);
        return saved;
    }

    private UserSettingsDto buildDto(String email, User user, UserSettings settings) {
        boolean passwordLoginAvailable = appProperties.isPasswordLoginEnabled();
        AccountStatusDto accountStatus = null;
        if (appProperties.isSubscriptionStatusEnabled()) {
            accountStatus = AccountStatusDto.builder()
                    .plan("Free")
                    .renewsAt(null)
                    .build();
        }

        return UserSettingsDto.builder()
                .email(email)
                .passwordEnabled(passwordLoginAvailable && user != null && user.getPassword() != null)
                .passwordLoginAvailable(passwordLoginAvailable)
                .mfaEnabled(user != null && user.isMfaEnabled())
                .mfaAvailable(true)
                .notifyNewContent(settings.isNotifyNewContent())
                .notifyEmail(settings.isNotifyEmail())
                .defaultQuality(settings.getDefaultQuality())
                .incognitoEnabled(settings.isIncognitoEnabled())
                .profilePrivate(settings.isProfilePrivate())
                .locale(settings.getLocale())
                .accountStatus(accountStatus)
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
