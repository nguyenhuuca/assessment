package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.usersettings.DeleteAccountRequest;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;

public interface UserSettingsService {

    /**
     * Returns the authenticated user's settings.
     * If no settings row exists yet, creates one with system defaults (lazy create).
     */
    UserSettingsDto getSettings();

    /**
     * Partially updates the authenticated user's preference fields.
     * All provided fields are validated atomically before any field is persisted.
     */
    UserSettingsDto updateSettings(UpdateUserSettingsRequest request);

    /**
     * Soft-deletes and anonymizes the authenticated user's account after step-up verification.
     */
    void deleteAccount(DeleteAccountRequest request);
}
