package com.canhlabs.funnyapp.dto;

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
