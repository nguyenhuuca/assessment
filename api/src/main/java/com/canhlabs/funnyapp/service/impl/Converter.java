package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.ShareLink;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.share.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.dto.VideoDto;

import java.util.List;
import java.util.stream.Collectors;

public class Converter {
    private Converter(){}
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