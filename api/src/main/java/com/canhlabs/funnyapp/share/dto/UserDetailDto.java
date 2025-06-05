package com.canhlabs.funnyapp.share.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDto extends BaseDto{
    private Long id;
    private String email;
    private boolean mfaEnabled = false;
}
