package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.service.ShareService;
import com.canhlabs.funnyapp.enums.ResultStatus;
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

    @Test
    void getPrivateShareLink_shouldReturnSuccessWithListOfVideos() {
        VideoDto video1 = VideoDto.builder().id(2L).build();
        VideoDto video2 = VideoDto.builder().id(3L).build();
        List<VideoDto> videos = Arrays.asList(video1, video2);
        when(shareService.getALLShare()).thenReturn(videos);

        ResponseEntity<ResultListInfo<VideoDto>> response = controller.getPrivateShareLink();

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(videos, response.getBody().getData());
        verify(shareService).getALLShare();
    }

    @Test
    void getPrivateShareLink_emptyList_shouldReturnSuccessWithEmptyList() {
        when(shareService.getALLShare()).thenReturn(List.of());

        ResponseEntity<ResultListInfo<VideoDto>> response = controller.getPrivateShareLink();

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void deleteShareLink_shouldReturnSuccessWithMessage() {
        when(shareService.deleteShareLink(10L)).thenReturn("Deleted successfully");

        ResponseEntity<ResultObjectInfo<String>> response = controller.deleteShareLink(10L);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals("Deleted successfully", response.getBody().getMessage());
        verify(shareService).deleteShareLink(10L);
    }

    @Test
    void deleteShareLink_whenServiceReturnsNullMessage_shouldReturnSuccessWithNullMessage() {
        when(shareService.deleteShareLink(99L)).thenReturn(null);

        ResponseEntity<ResultObjectInfo<String>> response = controller.deleteShareLink(99L);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertNull(response.getBody().getMessage());
        verify(shareService).deleteShareLink(99L);
    }

    @Test
    void shareLink_whenServiceThrowsException_shouldPropagateException() {
        ShareRequestDto request = new ShareRequestDto();
        when(shareService.shareLink(request)).thenThrow(new RuntimeException("Service error"));

        assertThrows(RuntimeException.class, () -> controller.shareLink(request));
        verify(shareService).shareLink(request);
    }

    @Test
    void getShareLink_whenServiceReturnsNull_shouldHandleNullData() {
        when(shareService.getALLShare()).thenReturn(null);

        ResponseEntity<ResultListInfo<VideoDto>> response = controller.getShareLink();

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertNull(response.getBody().getData());
    }
}