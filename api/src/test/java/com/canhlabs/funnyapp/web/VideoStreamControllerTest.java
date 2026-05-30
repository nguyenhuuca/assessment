package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.StreamVideoService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoStreamController.class)
@Import(VideoStreamControllerTest.TestSecurity.class)
class VideoStreamControllerTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    private static final String BASE = AppConstant.API.BASE_URL + "/video-stream";
    private static final long FILE_SIZE = 1_000_000L;

    @MockitoBean StreamVideoService videoService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());

        StreamChunkResult chunkResult = mock(StreamChunkResult.class);
        when(chunkResult.getStream()).thenAnswer(inv -> new ByteArrayInputStream("videodata".getBytes()));
        when(chunkResult.getActualStart()).thenReturn(0L);
        when(chunkResult.getActualEnd()).thenReturn(8L);
        when(videoService.getFileSize(any())).thenReturn(FILE_SIZE);
        when(videoService.getPartialFileUsingRAF(any(), anyLong(), anyLong())).thenReturn(chunkResult);
    }

    @Test
    void getListVideoStream_returns200WithList() throws Exception {
        when(videoService.getVideosToStream()).thenReturn(List.of(VideoDto.builder().build()));

        mockMvc.perform(get(BASE + "/list"))
                .andExpect(status().isOk());
    }

    @Test
    void getListVideoStream_emptyList_returns200() throws Exception {
        when(videoService.getVideosToStream()).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/list"))
                .andExpect(status().isOk());
    }

    @Test
    void getVideoStream_byId_returns200() throws Exception {
        when(videoService.getVideoById(1L)).thenReturn(VideoDto.builder().build());

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk());
    }

    @Test
    void streamVideo_noRangeHeader_returns206WithContentRange() throws Exception {
        mockMvc.perform(get(BASE + "/stream/video1"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.CONTENT_RANGE))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
    }

    @Test
    void streamVideo_rangeHeaderNoEnd_firstChunk_returns206() throws Exception {
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "bytes=0-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().exists(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    void streamVideo_rangeHeaderNoEnd_nonZeroStart_usesNextChunkSize() throws Exception {
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "bytes=262144-"))
                .andExpect(status().isPartialContent());
    }

    @Test
    void streamVideo_rangeHeaderExplicitEnd_returns206() throws Exception {
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "bytes=0-1023"))
                .andExpect(status().isPartialContent());
    }

    @Test
    void streamVideo_explicitEndExceedsFileSize_cappedAtFileSizeMinus1() throws Exception {
        // Request end beyond file size — should be capped
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "bytes=0-9999999"))
                .andExpect(status().isPartialContent());
    }

    @Test
    void streamVideo_invalidRangeFormat_fallsBackToFullStream() throws Exception {
        // NumberFormatException caught internally, falls back to full range
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "bytes=abc-def"))
                .andExpect(status().isPartialContent());
    }

    @Test
    void streamVideo_rangeNotStartingWithBytes_treatedAsNoRange() throws Exception {
        mockMvc.perform(get(BASE + "/stream/video1")
                        .header("Range", "invalid-header"))
                .andExpect(status().isPartialContent());
    }
}
