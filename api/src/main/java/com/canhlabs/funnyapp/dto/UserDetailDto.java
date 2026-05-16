package com.canhlabs.funnyapp.dto;

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
}
