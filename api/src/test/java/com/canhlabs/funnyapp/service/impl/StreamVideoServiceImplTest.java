package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.VideoCacheService;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StreamVideoServiceImplTest {
    @Mock
    private Drive drive;
    @Mock
    private VideoSourceRepository videoSourceRepository;
    @Mock
    private VideoCacheService videoCacheService;
    @Mock
    private ChatGptService chatGptService;

    @InjectMocks
    private StreamVideoServiceImpl streamVideoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        streamVideoService = new StreamVideoServiceImpl(drive);
        streamVideoService.injectRepo(videoSourceRepository);
        streamVideoService.injectCacheService(videoCacheService);
        streamVideoService.injectChatGptService(chatGptService);
    }

    @Test
    void testGetPartialFileByChunk_CacheHit() throws IOException {
        String fileId = "file123";
        long start = 0, end = 10;
        InputStream mockStream = new ByteArrayInputStream(new byte[]{1,2,3});
        when(videoCacheService.hasChunk(fileId, start, end)).thenReturn(true);
        when(videoCacheService.getChunk(fileId, start, end)).thenReturn(mockStream);

        StreamChunkResult result = streamVideoService.getPartialFileByChunk(fileId, start, end);
        assertNotNull(result);
        assertEquals(start, result.getActualStart());
        assertEquals(end, result.getActualEnd());
        assertEquals(mockStream, result.getStream());
    }

    @Test
    void testGetPartialFileUsingRAF() throws IOException {
        String fileId = "file123";
        long start = 0, end = 10;
        InputStream mockStream = new ByteArrayInputStream(new byte[]{1,2,3});
        when(videoCacheService.getFileRangeFromDisk(fileId, start, end)).thenReturn(mockStream);
        StreamChunkResult result = streamVideoService.getPartialFileUsingRAF(fileId, start, end);
        assertNotNull(result);
        assertEquals(start, result.getActualStart());
        assertEquals(end, result.getActualEnd());
        assertEquals(mockStream, result.getStream());
    }

    @Test
    void testGetFileSize() throws IOException {
        String fileId = "file123";
        long expectedSize = 12345L;
        when(videoCacheService.getFileSizeFromDisk(fileId)).thenReturn(expectedSize);
        long size = streamVideoService.getFileSize(fileId);
        assertEquals(expectedSize, size);
    }

    @Test
    void testGetVideosToStream() {
        VideoSource videoSource = VideoSource.builder().id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(videoSource));
        List<VideoDto> videos = streamVideoService.getVideosToStream();
        assertNotNull(videos);
        assertEquals(1, videos.size());
        assertEquals("src1", videos.get(0).getFileId());
    }

    @Test
    void testGetVideoById() {
        VideoSource videoSource = VideoSource.builder().id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findById(1L)).thenReturn(Optional.of(videoSource));
        VideoDto dto = streamVideoService.getVideoById(1L);
        assertNotNull(dto);
        assertEquals("src1", dto.getFileId());
    }

    @Test
    void testGetVideoBySourceId() {
        VideoSource videoSource = VideoSource.builder().id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findBySourceId("src1")).thenReturn(Optional.of(videoSource));
        VideoDto dto = streamVideoService.getVideoBySourceId("src1");
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
    }
}