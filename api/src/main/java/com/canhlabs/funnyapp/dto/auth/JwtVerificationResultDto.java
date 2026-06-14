package com.canhlabs.funnyapp.dto.auth;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
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
public class JwtVerificationResultDto  extends BaseDto {
    private Boolean successful;
    private String message;
    private int subCode;
    private int httpCode;
    private UserDetailDto data;
}
