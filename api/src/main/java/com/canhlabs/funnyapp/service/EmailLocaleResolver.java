package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.config.I18nConfig;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.UserSettings;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Locale;

/**
 * Picks the language of an outgoing email (ADR-0020 D6): the stored {@code user_settings.locale}, else the
 * current request's locale (only inside a web request), else English.
 * <p>
 * Call it on the calling thread, before any async boundary, and hand the result to the mail code as a plain
 * {@link Locale}; nothing downstream may read {@code LocaleContextHolder}.
 */
@Component
@RequiredArgsConstructor
public class EmailLocaleResolver {

    private final UserSettingsRepository settingsRepository;
    private final UserRepo userRepo;

    /** Locale for a known user id. */
    public Locale forUser(Long userId) {
        Locale stored = userId == null ? null : settingsRepository.findById(userId)
                .map(UserSettings::getLocale).map(EmailLocaleResolver::supported).orElse(null);
        return stored != null ? stored : forRequest();
    }

    /** Locale for an email address; a brand-new address (no user, no settings row) follows the request. */
    public Locale forEmail(String email) {
        User user = email == null ? null : userRepo.findAllByUserName(email);
        return forUser(user == null ? null : user.getId());
    }

    /** The current request's locale, or English when not inside a web request (jobs, async threads). */
    public Locale forRequest() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return I18nConfig.EN;
        }
        Locale requested = supported(LocaleContextHolder.getLocale().getLanguage());
        return requested != null ? requested : I18nConfig.EN;
    }

    private static Locale supported(String language) {
        return I18nConfig.SUPPORTED.stream()
                .filter(l -> l.getLanguage().equalsIgnoreCase(language))
                .findFirst().orElse(null);
    }
}
