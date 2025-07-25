package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.ShareLink;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.UserInfoDto;
import com.canhlabs.funnyapp.dto.VideoDto;

import java.util.List;
import java.util.stream.Collectors;

public class Converter {
    private Converter(){}
    public static UserInfoDto toUserInfo(User user, String token, String action, String sessionToken) {
        return UserInfoDto.builder()
                .jwt(token)
                .action(action)
                .sessionToken(sessionToken)
                .user(toUserDetail(user))
                .build();
    }

    public static UserInfoDto toUserInfo(User user, String token, String action) {
        return UserInfoDto.builder()
                .jwt(token)
                .action(action)
                .user(toUserDetail(user))
                .build();
    }

    public static UserInfoDto toUserInfo(User user, String token) {
        return UserInfoDto.builder()
                .jwt(token)
                .user(toUserDetail(user))
                .build();
    }
    public static UserDetailDto toUserDetail(User user) {
        return UserDetailDto.builder()
                .id(user.getId())
                .email(user.getUserName())
                .mfaEnabled(user.isMfaEnabled())
                .build();
    }

    public static List<VideoDto> videoDtoList(List<ShareLink> shareLinks) {
        return shareLinks.stream().map(link -> VideoDto
                .builder()
                .id(link.getId())
                .desc(link.getDesc())
                .title(link.getTitle())
                .embedLink(link.getEmbedLink())
                .urlLink(link.getUrlLink())
                .userShared(link.getUser().getUserName())
                .build()).collect(Collectors.toList());
    }
}