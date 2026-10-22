package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.ShareLink;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.dto.video.VideoDto;

import java.util.List;
import java.util.stream.Collectors;

public class Converter {
    /** Shown as the owner of stored videos, which have no sharing user. */
    private static final String DEFAULT_OWNER = "funnyapp";

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
                // FE derives isAdmin from this on login; without it the admin menu needs a reload
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .permissions(user.getPermissions())
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

    /** Feed-shaped DTO for a stored video; shared by the feed and collections. */
    public static VideoDto toVideoDto(VideoSource source) {
        VideoDto dto = new VideoDto();
        dto.setId(source.getId());
        dto.setFileId(source.getSourceId());
        dto.setUserShared(DEFAULT_OWNER);
        dto.setTitle(source.getTitle());
        dto.setDesc(source.getDesc());
        dto.setViewCount(source.getViewCount());
        dto.setUrlLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        dto.setEmbedLink("https://canh-labs.com/api/v1/funny-app/video-stream/stream/" + source.getSourceId());
        return dto;
    }
}
