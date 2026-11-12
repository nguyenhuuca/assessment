package com.canhlabs.funnyapp.dto.user;
import com.canhlabs.funnyapp.dto.BaseDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDto extends BaseDto{
    private Long id;
    private String email;
    private boolean mfaEnabled = false;
    private String role;
    private int permissions;
    private boolean passwordEnabled;
    private boolean mfaAvailable;
    /** Global kill switch state; lets the client show the password login tab. */
    private boolean passwordLoginAvailable;
    /** True while premiumUntil is in the future. */
    private boolean premium;
    /** End of the Premium period; null when never bought. */
    private Instant premiumUntil;
    /** JWT claim "cv". Internal: used for session revocation, never serialized to clients. */
    @JsonIgnore
    private int credentialsVersion;
}
