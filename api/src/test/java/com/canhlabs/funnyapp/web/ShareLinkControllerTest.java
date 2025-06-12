package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.service.ShareService;
import com.canhlabs.funnyapp.share.enums.ResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareLinkControllerTest {

    @Mock
    private ShareService shareService;

    @InjectMocks
    private ShareLinkController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller.injectUser(shareService);
    }

    @Test
    void shareLink_shouldReturnSuccessWithVideoDto() {
        ShareRequestDto request = new ShareRequestDto();
        VideoDto video = VideoDto.builder().id(1L).build();
        when(shareService.shareLink(request)).thenReturn(video);

        ResponseEntity<ResultObjectInfo<VideoDto>> response = controller.shareLink(request);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(video, response.getBody().getData());
        verify(shareService).shareLink(request);
    }

    @Test
    void getShareLink_shouldReturnSuccessWithListOfVideos() {
        VideoDto video1 = VideoDto.builder().id(1L).build();
        VideoDto video2 = VideoDto.builder().id(1L).build();
        List<VideoDto> videos = Arrays.asList(video1, video2);
        when(shareService.getALLShare()).thenReturn(videos);

        ResponseEntity<ResultListInfo<VideoDto>> response = controller.getShareLink();

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(videos, response.getBody().getData());
        verify(shareService).getALLShare();
    }
}