package com.canhlabs.funnyapp.dto.usersettings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for PATCH /user/settings.
 * All fields are nullable to support partial (merge-patch) semantics.
 * Fields absent from the body are left unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserSettingsRequest {
    private Boolean notifyNewContent;
    private Boolean notifyEmail;
    private String defaultQuality;
    private Boolean incognitoEnabled;
    private Boolean profilePrivate;
    /** 'vi' | 'en'. An explicit JSON null clears the preference; an absent field leaves it unchanged. */
    private String locale;
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean localeSet;

    /** Called by Jackson for both a value and an explicit null, so presence can be told from absence. */
    public void setLocale(String locale) {
        this.locale = locale;
        this.localeSet = true;
    }

    /** True when the client sent the locale field at all (value or explicit null). */
    @JsonIgnore
    public boolean isLocaleProvided() {
        return localeSet || locale != null;
    }
}
