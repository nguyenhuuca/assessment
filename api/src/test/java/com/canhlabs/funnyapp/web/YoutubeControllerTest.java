package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.service.YouTubeVideoService;
import com.canhlabs.funnyapp.enums.ResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YoutubeControllerTest {

    @Mock
    private YouTubeVideoService youTubeVideoService;

    @InjectMocks
    private YoutubeController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getTopVideos_shouldReturnSuccessWithListOfVideos() {
        VideoDto video1 = VideoDto.builder().id(1L).build();
        VideoDto video2 = VideoDto.builder().id(2L).build();
        List<VideoDto> videos = Arrays.asList(video1, video2);

        when(youTubeVideoService.getVideoIds()).thenReturn(videos);

        ResponseEntity<ResultListInfo<VideoDto>> response = controller.getTopVideos();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(videos, response.getBody().getData());
        verify(youTubeVideoService).getVideoIds();
    }
}