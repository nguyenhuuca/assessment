package com.canhlabs.funnyapp.dto.user;
import com.canhlabs.funnyapp.dto.BaseDto;

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
}
