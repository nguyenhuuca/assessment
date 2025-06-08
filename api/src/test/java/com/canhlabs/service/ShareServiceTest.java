package com.canhlabs.service;

import com.canhlabs.funnyapp.domain.ShareLink;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.repo.ShareLinkRepo;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.impl.ShareServiceImpl;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.dto.ShareRequestDto;
import com.canhlabs.funnyapp.share.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.dto.VideoDto;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@Disabled
@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private AppProperties props;
    @Mock
    private ShareLinkRepo shareLinkRepo;
    @Mock
    private UserRepo userRepo;


    ShareServiceImpl shareService = new ShareServiceImpl();
    @BeforeEach
    void setup(){
        shareService.injectShareLink(shareLinkRepo);
        shareService.injectProp(props);
        shareService.injectUser(userRepo);
    }

    @Test
    void getAllTest() {
        List<ShareLink> shareLinks = mockShareLinkList();
        when(shareLinkRepo.findByOrderByCreatedAtDesc()).thenReturn(shareLinks);
        List<VideoDto> rs = shareService.getALLShare();
        Assertions.assertThat(rs.size()).isEqualTo(2);
        Assertions.assertThat(rs.get(0).getTitle()).isEqualTo("test1");


    }

    @Test
    void shareLinkTest() {
        UsernamePasswordAuthenticationToken authentication;
        UserDetailDto user = UserDetailDto.builder().id(1L).email("ca@gmail.com").build();
        authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList());
        authentication.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ShareRequestDto shareRequestDto =ShareRequestDto.builder()
                .url("https://www.youtube.com/watch?v=17eavV-6Dhc&list=RD17eavV-6Dhc&start_radio=1")
                .build();
        ShareLink shareLink = ShareLink.builder()
                .id(1L)
                .user(User.builder().id(2L).userName("Ca1").build())
                .build();
        when(shareLinkRepo.save(ArgumentMatchers.any())).thenReturn(shareLink);
        //
        when(props.getGoogleApiKey()).thenReturn( System.getenv("GOOGLE_KEY"));
        when(props.getGooglePart()).thenReturn("snippet,contentDetails,statistics,status");
        VideoDto rs = shareService.shareLink(shareRequestDto);
        Assertions.assertThat(rs.getUserShared()).isEqualTo("Ca1");


    }

    List<ShareLink> mockShareLinkList(){
        ShareLink shareLink1 = ShareLink.builder()
                .title("test1")
                .user(User.builder().id(1L).userName("user1").build())
                .build();
        ShareLink shareLink2 = ShareLink.builder()
                .title("test2")
                .user(User.builder().id(2L).userName("user2").build())
                .build();

        List<ShareLink> shareLinks = new ArrayList<>();
        shareLinks.add(shareLink1);
        shareLinks.add(shareLink2);
        return  shareLinks;
    }
}
