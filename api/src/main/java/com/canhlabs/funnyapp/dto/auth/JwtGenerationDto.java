package com.canhlabs.funnyapp.dto.auth;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.BaseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class JwtGenerationDto extends BaseDto {
    private Long duration; // millisecond
    private UserDetailDto payload;
}
