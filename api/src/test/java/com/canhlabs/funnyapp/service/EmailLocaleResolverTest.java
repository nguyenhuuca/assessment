package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailLocaleResolverTest {

    @Mock UserSettingsRepository settingsRepository;
    @Mock UserRepo userRepo;

    EmailLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EmailLocaleResolver(settingsRepository, userRepo);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private static void inRequestWith(Locale locale) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        LocaleContextHolder.setLocale(locale);
    }

    private void stored(long userId, String locale) {
        when(settingsRepository.findById(userId)).thenReturn(
                Optional.of(UserSettings.builder().userId(userId).locale(locale).build()));
    }

    @Test
    void forUser_storedLocaleWins_overRequestLocale() {
        stored(1L, "vi");
        inRequestWith(Locale.ENGLISH);

        assertThat(resolver.forUser(1L)).isEqualTo(I18nConfig.VI);
    }

    @Test
    void forUser_storedLocaleWins_outsideAnyRequest() {
        stored(1L, "en");

        assertThat(resolver.forUser(1L)).isEqualTo(I18nConfig.EN);
    }

    @Test
    void forUser_noStoredLocale_followsRequest() {
        stored(1L, null);
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forUser(1L)).isEqualTo(I18nConfig.VI);
    }

    @Test
    void forUser_noSettingsRow_followsRequest() {
        when(settingsRepository.findById(2L)).thenReturn(Optional.empty());
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forUser(2L)).isEqualTo(I18nConfig.VI);
    }

    @Test
    void forUser_unsupportedStoredValue_fallsThroughToRequest() {
        stored(1L, "fr");
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forUser(1L)).isEqualTo(I18nConfig.VI);
    }

    @Test
    void forUser_nullId_followsRequest_withoutHittingTheRepository() {
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forUser(null)).isEqualTo(I18nConfig.VI);
        verifyNoInteractions(settingsRepository);
    }

    @Test
    void forRequest_outsideWebRequest_ignoresThreadLocale_andReturnsEnglish() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        assertThat(resolver.forRequest()).isEqualTo(I18nConfig.EN);
    }

    @Test
    void forRequest_unsupportedRequestLocale_returnsEnglish() {
        inRequestWith(Locale.FRENCH);

        assertThat(resolver.forRequest()).isEqualTo(I18nConfig.EN);
    }

    @Test
    void forEmail_newUser_usesRequestLocale() {
        when(userRepo.findAllByUserName("new@x.com")).thenReturn(null);
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forEmail("new@x.com")).isEqualTo(I18nConfig.VI);
    }

    @Test
    void forEmail_existingUser_usesStoredLocale() {
        User user = new User();
        user.setId(5L);
        when(userRepo.findAllByUserName("old@x.com")).thenReturn(user);
        stored(5L, "en");
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forEmail("old@x.com")).isEqualTo(I18nConfig.EN);
    }

    @Test
    void forEmail_nullEmail_followsRequest() {
        inRequestWith(Locale.forLanguageTag("vi"));

        assertThat(resolver.forEmail(null)).isEqualTo(I18nConfig.VI);
        verifyNoInteractions(userRepo);
    }
}
