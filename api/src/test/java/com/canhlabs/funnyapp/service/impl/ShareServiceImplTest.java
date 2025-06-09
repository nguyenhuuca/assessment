// File: src/test/java/com/canhlabs/funnyapp/service/impl/ShareServiceImplTest.java
package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.ShareLink;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.repo.ShareLinkRepo;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.dto.ShareRequestDto;
import com.canhlabs.funnyapp.share.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.dto.VideoDto;
import com.canhlabs.funnyapp.share.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}