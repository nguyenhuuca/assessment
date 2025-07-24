package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.domain.VideoSource;
import com.canhlabs.funnyapp.dto.StreamChunkResult;
import com.canhlabs.funnyapp.dto.VideoDto;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.google.api.services.drive.Drive;
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
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamVideoServiceImplTest {
    @Mock
    private Drive drive;
    @Mock
    private VideoSourceRepository videoSourceRepository;
    @Mock
    private VideoStorageService videoStorageService;
    @Mock
    private ChatGptService chatGptService;
    @Mock
    FfmpegService ffmpegService;
    @Mock
    AppProperties appProperties;

    @InjectMocks
    private StreamVideoServiceImpl streamVideoService;

    @BeforeEach
    void setUp() {
        streamVideoService = new StreamVideoServiceImpl(drive);
        streamVideoService.injectRepo(videoSourceRepository);
        streamVideoService.injectCacheService(videoStorageService);
        streamVideoService.injectChatGptService(chatGptService);
        streamVideoService.injectFfmpegService(ffmpegService);
        streamVideoService.injectAppProperties(appProperties);

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
    void testListFilesInFolder_ReturnsFiles() throws Exception {
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.List list = mock(Drive.Files.List.class);
        com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
        file.setId("id1");
        file.setName("name1");
        com.google.api.services.drive.model.FileList fileList = new com.google.api.services.drive.model.FileList();
        fileList.setFiles(List.of(file));
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList);
        List<com.google.api.services.drive.model.File> result = streamVideoService.listFilesInFolder("folder", "2024-01-01T00:00:00Z");
        assertEquals(1, result.size());
        assertEquals("id1", result.getFirst().getId());
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
        doReturn(List.of(file)).when(spyService).listFilesInFolder(anyString(), anyString());
        spyService.downloadFileFromFolder("folder", "2024-01-01T00:00:00Z");
        assertTrue(localFile.exists());
        localFile.delete();
        localFile.getParentFile().delete();
    }

    @Test
    void testDownloadFileFromFolder_DownloadsAndSavesInfo() throws Exception {
        StreamVideoServiceImpl spyService = spy(streamVideoService);
        com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
        file.setId("id2");
        file.setName("name2.mp4");
        when(appProperties.getImageStoragePath()).thenReturn("/var/test");
        when(appProperties.getImageUrl()).thenReturn("http://localhost:8080/images/");
        doReturn(List.of(file)).when(spyService).listFilesInFolder(anyString(), anyString());
        doNothing().when(ffmpegService).generateThumbnail(anyString(), anyString());
        doNothing().when(spyService).downloadFile(eq("id2"), any(java.io.File.class));
        doNothing().when(spyService).saveInfo(eq("id2"), anyString(), anyString());
        java.io.File localFile = new java.io.File("video-cache/id2.full");
        if (localFile.exists()) localFile.delete();
        spyService.downloadFileFromFolder("folder", "2024-01-01T00:00:00Z");
        // file should be created by downloadFile, but we mock it, so just check saveInfo called
        verify(spyService).saveInfo(eq("id2"), anyString(), anyString());
    }

    @Test
    void testDownloadFile_WritesToFile() throws Exception {
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.Get get = mock(Drive.Files.Get.class);
        when(drive.files()).thenReturn(files);
        when(files.get(anyString())).thenReturn(get);
        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write(1);
            return null;
        }).when(get).executeMediaAndDownloadTo(any(OutputStream.class));
        java.io.File dest = new java.io.File("video-cache/testfile.full");
        dest.getParentFile().mkdirs();
        if (dest.exists()) dest.delete();
        streamVideoService.downloadFile("id3", dest);
        assertTrue(dest.exists());
        dest.delete();
        dest.getParentFile().delete();
    }



    @Test
    void testShareFilesInFolder_Success() throws Exception {
        StreamVideoServiceImpl spyService = spy(streamVideoService);
        com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
        file.setId("id4");
        file.setName("name4.mp4");
        doReturn(List.of(file)).when(spyService).listFilesInFolder(any(Drive.class), anyString());
        doNothing().when(spyService).saveInfo(eq("id4"), anyString(), anyString());
        spyService.shareFilesInFolder();
        verify(spyService).saveInfo(eq("id4"), anyString(), anyString());
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

    @Test
    void testListFilesInFolder_WithDriveAndPaging() throws Exception {
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.List list = mock(Drive.Files.List.class);
        com.google.api.services.drive.model.File file1 = new com.google.api.services.drive.model.File();
        file1.setId("id1");
        file1.setName("name1");
        com.google.api.services.drive.model.File file2 = new com.google.api.services.drive.model.File();
        file2.setId("id2");
        file2.setName("name2");
        com.google.api.services.drive.model.FileList fileList1 = new com.google.api.services.drive.model.FileList();
        fileList1.setFiles(List.of(file1, file2));
        fileList1.setNextPageToken("token2");
        com.google.api.services.drive.model.FileList fileList2 = new com.google.api.services.drive.model.FileList();
        fileList2.setFiles(List.of(file2));
        fileList2.setNextPageToken("");
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(anyString())).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.setPageToken(anyString())).thenReturn(list);
        when(list.execute()).thenReturn(fileList1).thenReturn(fileList2);
        // Remove getPageToken mocking, not needed
        StreamVideoServiceImpl spyService = spy(streamVideoService);
        List<com.google.api.services.drive.model.File> result = spyService.listFilesInFolder(drive, "folderId");
        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getId());
        assertEquals("id2", result.get(1).getId());
    }
}