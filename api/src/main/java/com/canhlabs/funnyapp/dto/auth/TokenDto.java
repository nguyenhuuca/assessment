package com.canhlabs.funnyapp.dto.auth;
import com.canhlabs.funnyapp.dto.BaseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDto extends BaseDto {
    private String token;
}
