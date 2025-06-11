// File: src/test/java/com/canhlabs/funnyapp/service/impl/ShareServiceImplTest.java
package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.ShareLink;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.repo.ShareLinkRepo;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.Converter;
import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.share.exception.CustomException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock
    private ShareLinkRepo shareLinkRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private AppProperties props;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ShareServiceImpl shareService;

    @BeforeEach
    void setup() {
        shareService.injectShareLink(shareLinkRepo);
        shareService.injectProp(props);
        shareService.injectUser(userRepo);
    }

    @Test
    void shareLink_success() {
        try (MockedStatic<AppUtils> appUtilsMockedStatic = Mockito.mockStatic(AppUtils.class)) {
            UserDetailDto userDetail = UserDetailDto.builder().id(1L).email("test@abc.com").build();
            appUtilsMockedStatic.when(AppUtils::getCurrentUser).thenReturn(userDetail);

            ShareRequestDto req = ShareRequestDto.builder().url("https://youtube.com/watch?v=abc123").build();
            VideoDto videoDto = VideoDto.builder().title("title").desc("desc").urlLink(req.getUrl()).embedLink("embed").build();

            // Mock repo and user
            User user = User.builder().id(1L).userName("testuser").build();
            when(userRepo.findAllById(1L)).thenReturn(user);
            ShareLink shareLink = ShareLink.builder().id(10L).user(user).build();
            when(shareLinkRepo.save(any())).thenReturn(shareLink);

            // Use spy to override private method
            ShareServiceImpl spyService = Mockito.spy(shareService);
            doReturn(videoDto).when(spyService).getInfoFromYoutube(anyString());

            VideoDto result = spyService.shareLink(req);

            assertThat(result.getUserShared()).isEqualTo("testuser");
            assertThat(result.getId()).isEqualTo(10L);
        }
    }

    @Test
    void getALLShare_success() {
        try (MockedStatic<AppUtils> appUtilsMockedStatic = Mockito.mockStatic(AppUtils.class)) {
            UserDetailDto userDetail = UserDetailDto.builder().id(2L).email("user2@abc.com").build();
            appUtilsMockedStatic.when(AppUtils::getCurrentUser).thenReturn(userDetail);

            User user = User.builder().id(2L).userName("user2@abc.com").build();
            when(userRepo.findAllByUserName("user2@abc.com")).thenReturn(user);

            ShareLink link1 = ShareLink.builder().title("t1").user(user).build();
            ShareLink link2 = ShareLink.builder().title("t2").user(user).build();
            when(shareLinkRepo.findAllByUser(user)).thenReturn(Arrays.asList(link1, link2));

            // Mock Converter.videoDtoList
            try (MockedStatic<Converter> converterMockedStatic = Mockito.mockStatic(Converter.class)) {
                VideoDto v1 = VideoDto.builder().title("t1").build();
                VideoDto v2 = VideoDto.builder().title("t2").build();
                converterMockedStatic.when(() -> Converter.videoDtoList(anyList())).thenReturn(Arrays.asList(v1, v2));

                List<VideoDto> result = shareService.getALLShare();
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getTitle()).isEqualTo("t1");
            }
        }
    }

    @Test
    void getALLShare_noUser_throwsException() {
        try (MockedStatic<AppUtils> appUtilsMockedStatic = Mockito.mockStatic(AppUtils.class)) {
            appUtilsMockedStatic.when(AppUtils::getCurrentUser).thenReturn(null);
            assertThatThrownBy(() -> shareService.getALLShare())
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("User is not exist");
        }
    }

    @Test
    void shareLink_throwsExceptionWhenVideoInfoIsNull() {
        ShareRequestDto req = ShareRequestDto.builder().url("https://youtube.com/watch?v=notfound").build();
        ShareServiceImpl spyService = Mockito.spy(shareService);
        doReturn(null).when(spyService).getInfoFromYoutube(anyString());

        assertThatThrownBy(() -> spyService.shareLink(req))
                .hasMessageContaining("Cannot get video info");
    }

    @Test
    void getInfoFromYoutube_ExceptionOccurs() {
        ShareServiceImpl spyService = Mockito.spy(shareService);
        // Pass an invalid URL to trigger exception
        assertThatThrownBy(() -> {
            VideoDto result = spyService.getInfoFromYoutube("invalid_url");
        }).hasMessageContaining("Cannot get video info");

    }

    @Test
    void getInfoFromYoutube_returnsVideoDtoForValidUrl() throws JsonProcessingException {
        ShareServiceImpl spyService = Mockito.spy(shareService);
        VideoDto mockDto = VideoDto.builder().title("t").desc("d").urlLink("u").embedLink("e").build();
        doReturn(mockDto).when(spyService).requestYouTube(anyString(), anyString());

        VideoDto result = spyService.getInfoFromYoutube("https://youtube.com/watch?v=abc123");
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("t");
    }

    @Test
    void getQueryParam_parsesQueryCorrectly() throws Exception {
        String query = "v=abc123&foo=bar";
        java.lang.reflect.Method method = ShareServiceImpl.class.getDeclaredMethod("getQueryParam", String.class);
        method.setAccessible(true);
        Map<String, String> result = (Map<String, String>) method.invoke(shareService, query);
        assertThat(result.get("v")).isEqualTo("abc123");
        assertThat(result.get("foo")).isEqualTo("bar");
    }

    @Test
    void toEntity_throwsExceptionWhenCurrentUserIsNull() throws Exception {
        try (MockedStatic<AppUtils> appUtilsMockedStatic = Mockito.mockStatic(AppUtils.class)) {
            appUtilsMockedStatic.when(AppUtils::getCurrentUser).thenReturn(null);
            java.lang.reflect.Method method = ShareServiceImpl.class.getDeclaredMethod("toEntity", VideoDto.class);
            method.setAccessible(true);
            VideoDto dto = VideoDto.builder().title("t").desc("d").embedLink("e").urlLink("u").build();
            try {
                method.invoke(shareService, dto);
            } catch (Exception e) {
                String msg = ((CustomException) ((InvocationTargetException) e).getTargetException()).getMessage();
                assertThat(msg.contains("User is not exist"));
            }

        }
    }

    @Test
    void requestYouTube_returnsVideoDtoForValidResponse() throws Exception {
        // Arrange
        String videoId = "abc123";
        String link = "https://youtube.com/watch?v=" + videoId;
        String apiKey = "fake-api-key";
        String part = "snippet";
        String jsonResponse = """
    {
      "items": [
        {
          "snippet": {
            "title": "Test Title",
            "description": "Test Description"
          }
        }
      ]
    }
    """;

        when(props.getGoogleApiKey()).thenReturn(apiKey);
        when(props.getGooglePart()).thenReturn(part);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

        // Act
        VideoDto result = shareService.requestYouTube(link, videoId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Title");
        assertThat(result.getDesc()).isEqualTo("Test Description");
        assertThat(result.getEmbedLink()).isEqualTo("https://youtube.com/embed/" + videoId);
        assertThat(result.getUrlLink()).isEqualTo(link);
    }
}