package com.canhlabs.funnyapp.dto.usersettings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettingsDto {
    private String email;
    private boolean passwordEnabled;
    private boolean mfaEnabled;
    private boolean mfaAvailable;
    private boolean notifyNewContent;
    private boolean notifyEmail;
    private String defaultQuality;
    private boolean incognitoEnabled;
    private boolean profilePrivate;
    private AccountStatusDto accountStatus;
    private Instant updatedAt;
}
