package com.canhlabs.assessment.service.impl;

import com.canhlabs.assessment.domain.User;
import com.canhlabs.assessment.share.dto.UserDetailDto;
import com.canhlabs.assessment.share.dto.UserInfoDto;

public class Converter {
    private Converter(){}
    public static UserInfoDto toUserInfo(User user) {
        return UserInfoDto.builder()
                .jwt("test")
                .user(toUserDetail(user))
                .build();
    }
    public static UserDetailDto toUserDetail(User user) {
        return UserDetailDto.builder()
                .id(user.getId())
                .email(user.getUserName())
                .build();
    }

}
