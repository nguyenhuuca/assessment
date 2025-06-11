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
public class JwtVerificationResultDto  extends BaseDto {
    private Boolean successful;
    private String message;
    private int subCode;
    private int httpCode;
    private UserDetailDto data;
}
