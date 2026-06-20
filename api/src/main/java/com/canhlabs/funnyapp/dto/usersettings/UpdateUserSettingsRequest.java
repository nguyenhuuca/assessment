package com.canhlabs.funnyapp.dto.usersettings;

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
}
