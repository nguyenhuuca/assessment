package com.canhlabs.funnyapp.share.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserInfoDto extends BaseDto {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String jwt;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String action;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UserDetailDto user;
}
