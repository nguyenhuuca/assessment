// File: src/test/java/com/canhlabs/funnyapp/share/ConverterTest.java
package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.ShareLink;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.UserInfoDto;
import com.canhlabs.funnyapp.dto.VideoDto;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConverterTest {

    @Test
    void toUserInfo_withAllParams_returnsUserInfoDto() {
        User user = new User();
        user.setId(1L);
        user.setUserName("test@example.com");
        user.setMfaEnabled(true);

        UserInfoDto dto = Converter.toUserInfo(user, "token", "login", "session123");
        assertThat(dto.getJwt()).isEqualTo("token");
        assertThat(dto.getAction()).isEqualTo("login");
        assertThat(dto.getSessionToken()).isEqualTo("session123");
        assertThat(dto.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void toUserInfo_withTokenAndAction_returnsUserInfoDto() {
        User user = new User();
        user.setId(2L);
        user.setUserName("user2@example.com");
        user.setMfaEnabled(false);

        UserInfoDto dto = Converter.toUserInfo(user, "token2", "register");
        assertThat(dto.getJwt()).isEqualTo("token2");
        assertThat(dto.getAction()).isEqualTo("register");
        assertThat(dto.getUser().getEmail()).isEqualTo("user2@example.com");
    }

    @Test
    void toUserInfo_withTokenOnly_returnsUserInfoDto() {
        User user = new User();
        user.setId(3L);
        user.setUserName("user3@example.com");
        user.setMfaEnabled(false);

        UserInfoDto dto = Converter.toUserInfo(user, "token3");
        assertThat(dto.getJwt()).isEqualTo("token3");
        assertThat(dto.getAction()).isNull();
        assertThat(dto.getUser().getEmail()).isEqualTo("user3@example.com");
    }

    @Test
    void toUserDetail_returnsUserDetailDto() {
        User user = new User();
        user.setId(4L);
        user.setUserName("user4@example.com");
        user.setMfaEnabled(true);

        UserDetailDto detail = Converter.toUserDetail(user);
        assertThat(detail.getId()).isEqualTo(4L);
        assertThat(detail.getEmail()).isEqualTo("user4@example.com");
        assertThat(detail.isMfaEnabled()).isTrue();
    }

    @Test
    void videoDtoList_returnsListOfVideoDto() {
        User user = new User();
        user.setId(5L);
        user.setUserName("user5@example.com");
        user.setMfaEnabled(false);

        ShareLink link = new ShareLink();
        link.setId(10L);
        link.setDesc("desc");
        link.setTitle("title");
        link.setEmbedLink("embed");
        link.setUrlLink("url");
        link.setUser(user);

        List<VideoDto> dtos = Converter.videoDtoList(Collections.singletonList(link));
        assertThat(dtos).hasSize(1);
        VideoDto dto = dtos.get(0);
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getDesc()).isEqualTo("desc");
        assertThat(dto.getTitle()).isEqualTo("title");
        assertThat(dto.getEmbedLink()).isEqualTo("embed");
        assertThat(dto.getUrlLink()).isEqualTo("url");
        assertThat(dto.getUserShared()).isEqualTo("user5@example.com");
    }
}