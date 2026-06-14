package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.VideoCache;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.streaming.StreamChunkResult;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamVideoServiceImplTest {

    @Mock
    private VideoSourceRepository videoSourceRepository;

    @Mock
    private VideoStorageService videoStorageService;

    @Mock
    private ChatGptService chatGptService;

    @Mock
    private VideoCache videoCache;

    private StreamVideoServiceImpl streamVideoService;

    @BeforeEach
    void setUp() {
        streamVideoService = new StreamVideoServiceImpl();
        streamVideoService.injectRepo(videoSourceRepository);
        streamVideoService.injectCacheService(videoStorageService);
        streamVideoService.injectChatGptService(chatGptService);
        streamVideoService.injectVideoCacheStore(videoCache);
    }

    // -----------------------------------------------------------------------
    // getPartialFileUsingRAF
    // -----------------------------------------------------------------------

    @Test
    void getPartialFileUsingRAF_returnsCachedStream() throws Exception {
        String fileId = "file123";
        long start = 0L;
        long end = 99L;
        byte[] data = new byte[]{1, 2, 3};
        InputStream expectedStream = new ByteArrayInputStream(data);

        when(videoCache.getChunkStream(eq(fileId), eq(start), eq(end), any(Callable.class)))
                .thenReturn(expectedStream);

        StreamChunkResult result = streamVideoService.getPartialFileUsingRAF(fileId, start, end);

        assertNotNull(result);
        assertEquals(start, result.getActualStart());
        assertEquals(end, result.getActualEnd());
        assertSame(expectedStream, result.getStream());
        verify(videoCache).getChunkStream(eq(fileId), eq(start), eq(end), any(Callable.class));
    }

    @Test
    void getPartialFileUsingRAF_cacheMissInvokesStorageService() throws Exception {
        String fileId = "fileMiss";
        long start = 10L;
        long end = 50L;
        byte[] diskData = "hello".getBytes();
        InputStream diskStream = new ByteArrayInputStream(diskData);

        // Simulate cache-miss: actually call the loader callback
        when(videoCache.getChunkStream(eq(fileId), eq(start), eq(end), any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<byte[]> loader = invocation.getArgument(3);
                    byte[] loaded = loader.call();
                    return new ByteArrayInputStream(loaded);
                });
        when(videoStorageService.getFileRangeFromDisk(fileId, start, end)).thenReturn(diskStream);

        StreamChunkResult result = streamVideoService.getPartialFileUsingRAF(fileId, start, end);

        assertNotNull(result);
        assertEquals(start, result.getActualStart());
        assertEquals(end, result.getActualEnd());
        assertNotNull(result.getStream());
        verify(videoStorageService).getFileRangeFromDisk(fileId, start, end);
    }

    // -----------------------------------------------------------------------
    // getFileSize
    // -----------------------------------------------------------------------

    @Test
    void getFileSize_returnsExpectedSize() throws IOException {
        String fileId = "file123";
        long expectedSize = 12345L;
        when(videoStorageService.getFileSizeFromDisk(fileId)).thenReturn(expectedSize);

        long size = streamVideoService.getFileSize(fileId);

        assertEquals(expectedSize, size);
        verify(videoStorageService).getFileSizeFromDisk(fileId);
    }

    @Test
    void getFileSize_propagatesIOException() throws IOException {
        String fileId = "badFile";
        when(videoStorageService.getFileSizeFromDisk(fileId)).thenThrow(new IOException("disk error"));

        assertThrows(IOException.class, () -> streamVideoService.getFileSize(fileId));
    }

    // -----------------------------------------------------------------------
    // getVideosToStream
    // -----------------------------------------------------------------------

    @Test
    void getVideosToStream_returnsMappedDtos() {
        VideoSource source = VideoSource.builder()
                .id(1L).sourceId("src1").title("My Title").desc("My Desc").build();
        when(videoSourceRepository.findAllByIsHideOrderByCreatedAtDesc(Boolean.FALSE))
                .thenReturn(List.of(source));

        List<VideoDto> videos = streamVideoService.getVideosToStream();

        assertNotNull(videos);
        assertEquals(1, videos.size());
        VideoDto dto = videos.getFirst();
        assertEquals("src1", dto.getFileId());
        assertEquals("My Title", dto.getTitle());
        assertEquals("My Desc", dto.getDesc());
        assertEquals(1L, dto.getId());
    }

    @Test
    void getVideosToStream_returnsEmptyListWhenNoVideos() {
        when(videoSourceRepository.findAllByIsHideOrderByCreatedAtDesc(Boolean.FALSE))
                .thenReturn(Collections.emptyList());

        List<VideoDto> videos = streamVideoService.getVideosToStream();

        assertNotNull(videos);
        assertTrue(videos.isEmpty());
    }

    // -----------------------------------------------------------------------
    // getVideoById
    // -----------------------------------------------------------------------

    @Test
    void getVideoById_returnsDto_whenFound() {
        VideoSource source = VideoSource.builder()
                .id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findById(1L)).thenReturn(Optional.of(source));

        VideoDto dto = streamVideoService.getVideoById(1L);

        assertNotNull(dto);
        assertEquals("src1", dto.getFileId());
        assertEquals(1L, dto.getId());
    }

    @Test
    void getVideoById_throwsIllegalArgumentException_whenNotFound() {
        when(videoSourceRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> streamVideoService.getVideoById(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    // -----------------------------------------------------------------------
    // getVideoBySourceId
    // -----------------------------------------------------------------------

    @Test
    void getVideoBySourceId_returnsDto_whenFound() {
        VideoSource source = VideoSource.builder()
                .id(1L).sourceId("src1").title("title").desc("desc").build();
        when(videoSourceRepository.findBySourceId("src1")).thenReturn(Optional.of(source));

        VideoDto dto = streamVideoService.getVideoBySourceId("src1");

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("src1", dto.getFileId());
    }

    @Test
    void getVideoBySourceId_throwsIllegalArgumentException_whenNotFound() {
        when(videoSourceRepository.findBySourceId("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> streamVideoService.getVideoBySourceId("missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    // -----------------------------------------------------------------------
    // updateDesc
    // -----------------------------------------------------------------------

    @Test
    void updateDesc_updatesAndSavesDescription() {
        VideoSource source = VideoSource.builder().id(1L).title("Song Title").desc("").build();
        when(videoSourceRepository.findAllByDescIsNullOrDesc("")).thenReturn(List.of(source));
        when(chatGptService.makePoem("Song Title")).thenReturn("A lovely poem");
        when(videoSourceRepository.save(any())).thenReturn(source);

        streamVideoService.updateDesc();

        assertEquals("A lovely poem", source.getDesc());
        verify(chatGptService).makePoem("Song Title");
        verify(videoSourceRepository).save(source);
    }

    @Test
    void updateDesc_doesNothingWhenNoPendingVideos() {
        when(videoSourceRepository.findAllByDescIsNullOrDesc("")).thenReturn(Collections.emptyList());

        streamVideoService.updateDesc();

        verifyNoInteractions(chatGptService);
        verify(videoSourceRepository, never()).save(any());
    }

    @Test
    void updateDesc_processesMultipleSources() {
        VideoSource s1 = VideoSource.builder().id(1L).title("Title 1").desc(null).build();
        VideoSource s2 = VideoSource.builder().id(2L).title("Title 2").desc("").build();
        when(videoSourceRepository.findAllByDescIsNullOrDesc("")).thenReturn(List.of(s1, s2));
        when(chatGptService.makePoem("Title 1")).thenReturn("Poem 1");
        when(chatGptService.makePoem("Title 2")).thenReturn("Poem 2");
        when(videoSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streamVideoService.updateDesc();

        assertEquals("Poem 1", s1.getDesc());
        assertEquals("Poem 2", s2.getDesc());
        verify(chatGptService, times(2)).makePoem(any());
        verify(videoSourceRepository, times(2)).save(any(VideoSource.class));
    }

    // -----------------------------------------------------------------------
    // toDto – URL construction
    // -----------------------------------------------------------------------

    @Test
    void toDto_buildsCorrectUrlsFromSourceId() {
        VideoSource source = VideoSource.builder()
                .id(42L).sourceId("abc123").title("Test Video").desc("Some description").build();

        VideoDto dto = streamVideoService.toDto(source);

        String expectedUrl = "https://canh-labs.com/api/v1/funny-app/video-stream/stream/abc123";
        assertEquals(expectedUrl, dto.getUrlLink());
        assertEquals(expectedUrl, dto.getEmbedLink());
        assertEquals("unknown", dto.getUserShared());
    }

    @Test
    void toDto_mapsAllFieldsCorrectly() {
        VideoSource source = VideoSource.builder()
                .id(7L).sourceId("xyz").title("My Movie").desc("Great film").build();

        VideoDto dto = streamVideoService.toDto(source);

        assertEquals(7L, dto.getId());
        assertEquals("xyz", dto.getFileId());
        assertEquals("My Movie", dto.getTitle());
        assertEquals("Great film", dto.getDesc());
    }
}
