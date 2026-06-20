package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.usersettings.DeleteAccountRequest;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.totp.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSettingsServiceImplTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private UserRepo userRepo;

    @Mock
    private AppProperties appProperties;

    @Mock
    private Totp totp;

    @InjectMocks
    private UserSettingsServiceImpl userSettingsService;

    private static final UserDetailDto CURRENT_USER =
            UserDetailDto.builder().id(1L).email("a@b.com").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ── getSettings ──────────────────────────────────────────────────────────

    @Test
    void getSettings_noRow_createsDefaults() {
        UserSettings defaultSettings = new UserSettings();
        defaultSettings.setUserId(1L);
        defaultSettings.setNotifyNewContent(true);
        defaultSettings.setNotifyEmail(true);
        defaultSettings.setDefaultQuality("AUTO");
        defaultSettings.setIncognitoEnabled(false);
        defaultSettings.setProfilePrivate(false);

        User user = User.builder().id(1L).userName("a@b.com").build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());
            when(userSettingsRepository.save(any(UserSettings.class))).thenReturn(defaultSettings);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(false);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.getSettings();

            assertThat(dto.isNotifyNewContent()).isTrue();
            assertThat(dto.isNotifyEmail()).isTrue();
            assertThat(dto.getDefaultQuality()).isEqualTo("AUTO");
            assertThat(dto.isIncognitoEnabled()).isFalse();
            assertThat(dto.isProfilePrivate()).isFalse();
            verify(userSettingsRepository).save(any(UserSettings.class));
        }
    }

    @Test
    void getSettings_existingRow_returnsIt() {
        UserSettings existing = new UserSettings();
        existing.setUserId(1L);
        existing.setNotifyNewContent(false);
        existing.setNotifyEmail(false);
        existing.setDefaultQuality("1080P");
        existing.setIncognitoEnabled(true);
        existing.setProfilePrivate(true);

        User user = User.builder().id(1L).userName("a@b.com").mfaEnabled(true).build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(false);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.getSettings();

            assertThat(dto.isNotifyNewContent()).isFalse();
            assertThat(dto.isNotifyEmail()).isFalse();
            assertThat(dto.getDefaultQuality()).isEqualTo("1080P");
            assertThat(dto.isIncognitoEnabled()).isTrue();
            assertThat(dto.isProfilePrivate()).isTrue();
            assertThat(dto.isMfaEnabled()).isTrue();
            // findById was called but save was NOT called (no new row)
            verify(userSettingsRepository, never()).save(any());
        }
    }

    // ── updateSettings ────────────────────────────────────────────────────────

    @Test
    void updateSettings_emptyBody_throws400() {
        UpdateUserSettingsRequest req = new UpdateUserSettingsRequest();

        assertThatThrownBy(() -> userSettingsService.updateSettings(req))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ce.getMessage()).isEqualTo("Empty update");
                });

        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_invalidQuality_throws400_andSaveNeverCalled() {
        UpdateUserSettingsRequest req = UpdateUserSettingsRequest.builder()
                .defaultQuality("INVALID_VALUE")
                .build();

        assertThatThrownBy(() -> userSettingsService.updateSettings(req))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ce.getMessage()).isEqualTo("Invalid value for field 'defaultQuality'");
                });

        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_partial_appliesOnlyProvidedField() {
        UserSettings existing = new UserSettings();
        existing.setUserId(1L);
        existing.setNotifyNewContent(true);
        existing.setNotifyEmail(true);
        existing.setDefaultQuality("AUTO");
        existing.setIncognitoEnabled(false);
        existing.setProfilePrivate(false);

        User user = User.builder().id(1L).userName("a@b.com").build();

        UpdateUserSettingsRequest req = UpdateUserSettingsRequest.builder()
                .notifyEmail(false)
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userSettingsRepository.save(any(UserSettings.class))).thenReturn(existing);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(false);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.updateSettings(req);

            // notifyEmail changed
            assertThat(dto.isNotifyEmail()).isFalse();
            // others remain unchanged
            assertThat(dto.isNotifyNewContent()).isTrue();
            assertThat(dto.getDefaultQuality()).isEqualTo("AUTO");
            verify(userSettingsRepository).save(existing);
        }
    }

    @Test
    void updateSettings_validQuality_persists() {
        UserSettings existing = new UserSettings();
        existing.setUserId(1L);
        existing.setDefaultQuality("AUTO");

        User user = User.builder().id(1L).userName("a@b.com").build();

        UpdateUserSettingsRequest req = UpdateUserSettingsRequest.builder()
                .defaultQuality("1080P")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userSettingsRepository.save(any(UserSettings.class))).thenReturn(existing);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(false);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.updateSettings(req);

            assertThat(dto.getDefaultQuality()).isEqualTo("1080P");
            verify(userSettingsRepository).save(existing);
        }
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_alreadyDeactivated_throws409() {
        User user = User.builder().id(1L).userName("a@b.com").build();
        user.setStatus(UserStatus.DEACTIVATED);

        DeleteAccountRequest req = DeleteAccountRequest.builder()
                .confirmation("a@b.com")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userRepo.findAllById(1L)).thenReturn(user);

            assertThatThrownBy(() -> userSettingsService.deleteAccount(req))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(ce.getSubCode()).isEqualTo(409);
                        assertThat(ce.getMessage()).isEqualTo("Account already deactivated");
                    });
        }
    }

    @Test
    void deleteAccount_mfaUser_invalidOtp_throws403() {
        User user = User.builder().id(1L).userName("a@b.com").mfaEnabled(true).mfaSecret("SECRET").build();

        DeleteAccountRequest req = DeleteAccountRequest.builder()
                .otp("000000")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(totp.verify("000000", "SECRET")).thenReturn(false);

            assertThatThrownBy(() -> userSettingsService.deleteAccount(req))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ce.getSubCode()).isEqualTo(403);
                        assertThat(ce.getMessage()).isEqualTo("Invalid OTP");
                    });

            verify(userRepo, never()).save(any());
        }
    }

    @Test
    void deleteAccount_mfaUser_validOtp_softDeletes() {
        User user = User.builder().id(1L).userName("a@b.com").mfaEnabled(true).mfaSecret("SECRET").build();

        DeleteAccountRequest req = DeleteAccountRequest.builder()
                .otp("123456")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(totp.verify("123456", "SECRET")).thenReturn(true);
            when(userRepo.save(any(User.class))).thenReturn(user);

            userSettingsService.deleteAccount(req);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
            assertThat(user.getDeletedAt()).isNotNull();
            assertThat(user.getUserName()).startsWith("deleted_user_");
            assertThat(user.getPassword()).isNull();
            assertThat(user.getMfaSecret()).isNull();
            assertThat(user.isMfaEnabled()).isFalse();
            verify(userRepo).save(user);
        }
    }

    @Test
    void deleteAccount_nonMfaUser_wrongConfirmation_throws400() {
        User user = User.builder().id(1L).userName("a@b.com").mfaEnabled(false).build();

        DeleteAccountRequest req = DeleteAccountRequest.builder()
                .confirmation("wrong@email.com")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userRepo.findAllById(1L)).thenReturn(user);

            assertThatThrownBy(() -> userSettingsService.deleteAccount(req))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ce.getSubCode()).isEqualTo(400);
                        assertThat(ce.getMessage()).isEqualTo("Confirmation does not match");
                    });

            verify(userRepo, never()).save(any());
        }
    }

    @Test
    void deleteAccount_nonMfaUser_matchingConfirmation_softDeletes() {
        User user = User.builder().id(1L).userName("a@b.com").mfaEnabled(false).build();

        DeleteAccountRequest req = DeleteAccountRequest.builder()
                .confirmation("a@b.com")
                .build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(userRepo.save(any(User.class))).thenReturn(user);

            userSettingsService.deleteAccount(req);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
            assertThat(user.getDeletedAt()).isNotNull();
            assertThat(user.getUserName()).startsWith("deleted_user_");
            assertThat(user.getPassword()).isNull();
            assertThat(user.getMfaSecret()).isNull();
            assertThat(user.isMfaEnabled()).isFalse();
            verify(userRepo).save(user);
        }
    }

    // ── accountStatus / subscriptionStatusEnabled flag ────────────────────────

    @Test
    void getSettings_subscriptionEnabled_dtoHasAccountStatus() {
        UserSettings existing = new UserSettings();
        existing.setUserId(1L);
        existing.setDefaultQuality("AUTO");

        User user = User.builder().id(1L).userName("a@b.com").build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(true);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.getSettings();

            assertThat(dto.getAccountStatus()).isNotNull();
            assertThat(dto.getAccountStatus().getPlan()).isEqualTo("Free");
        }
    }

    @Test
    void getSettings_subscriptionDisabled_dtoHasNullAccountStatus() {
        UserSettings existing = new UserSettings();
        existing.setUserId(1L);
        existing.setDefaultQuality("AUTO");

        User user = User.builder().id(1L).userName("a@b.com").build();

        try (MockedStatic<AppUtils> ms = mockStatic(AppUtils.class)) {
            ms.when(AppUtils::getCurrentUser).thenReturn(CURRENT_USER);
            when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepo.findAllById(1L)).thenReturn(user);
            when(appProperties.isSubscriptionStatusEnabled()).thenReturn(false);
            when(appProperties.isUsePasswordless()).thenReturn(false);

            UserSettingsDto dto = userSettingsService.getSettings();

            assertThat(dto.getAccountStatus()).isNull();
        }
    }
}
