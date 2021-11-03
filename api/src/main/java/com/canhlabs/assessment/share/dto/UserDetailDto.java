package com.canhlabs.assessment.share.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDto extends BaseDto{
    private Long id;
    private String email;
}
