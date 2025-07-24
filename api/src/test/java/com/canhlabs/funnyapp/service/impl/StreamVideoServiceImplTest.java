package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamVideoServiceImplTest {
    @Mock
    private VideoSourceRepository videoSourceRepository;
    @Mock
    private VideoStorageService videoStorageService;
    @Mock
    private ChatGptService chatGptService;
    @InjectMocks
    private StreamVideoServiceImpl streamVideoService;

    @BeforeEach
    void setUp() {
        streamVideoService = new StreamVideoServiceImpl();
        streamVideoService.injectRepo(videoSourceRepository);
        streamVideoService.injectCacheService(videoStorageService);
        streamVideoService.injectChatGptService(chatGptService);

    }

    @Disabled
    @Test
    void testGetPartialFileUsingRAF() throws IOException {
        String fileId = "file123";
        long start = 0, end = 10;
        InputStream mockStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(videoStorageService.getFileRangeFromDisk(fileId, start, end)).thenReturn(mockStream);
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
        when(videoStorageService.getFileSizeFromDisk(fileId)).thenReturn(expectedSize);
        long size = streamVideoService.getFileSize(fileId);
        assertEquals(expectedSize, size);
    }

    @Test
    void testGetVideosToStream() {
        VideoSource videoSource = VideoSource.builder().id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findAllByIsHideOrderByCreatedAtDesc(Boolean.FALSE)).thenReturn(List.of(videoSource));
        List<VideoDto> videos = streamVideoService.getVideosToStream();
        assertNotNull(videos);
        assertEquals(1, videos.size());
        assertEquals("src1", videos.getFirst().getFileId());
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

    @Test
    void testDownloadFileFromFolder_SkipIfExists() throws Exception {
        StreamVideoServiceImpl spyService = spy(streamVideoService);
        com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
        file.setId("id1");
        file.setName("name1.mp4");
        java.io.File localFile = new java.io.File("video-cache/id1.full");
        localFile.getParentFile().mkdirs();
        localFile.createNewFile();
        assertTrue(localFile.exists());
        localFile.delete();
        localFile.getParentFile().delete();
    }


    @Test
    void testUpdateDesc_UpdatesMissingDesc() {
        VideoSource source = VideoSource.builder().id(1L).title("title").desc("").build();
        when(videoSourceRepository.findAllByDescIsNullOrDesc("")).thenReturn(List.of(source));
        when(chatGptService.makePoem("title")).thenReturn("desc");
        when(videoSourceRepository.save(any())).thenReturn(source);
        streamVideoService.updateDesc();
        verify(videoSourceRepository).save(any(VideoSource.class));
    }

}