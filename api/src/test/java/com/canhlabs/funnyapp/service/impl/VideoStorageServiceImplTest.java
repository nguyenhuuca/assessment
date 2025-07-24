package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import com.canhlabs.funnyapp.cache.LockManager;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class VideoStorageServiceImplTest {
    @Mock
    private LockManager lockManager;
    @Mock
    private StatsCache statsCache;
    @Mock
    VideoAccessService videoAccessService;
    @Mock
    private Drive drive;
    @Mock
    private VideoSourceRepository videoSourceRepository;
    @Mock
    private ChatGptService chatGptService;
    @Mock
    FfmpegService ffmpegService;
    @Mock
    AppProperties appProperties;

    @InjectMocks
    private VideoStorageServiceImpl videoStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        videoStorageService = new VideoStorageServiceImpl();
        videoStorageService.injectCacheStatsService(statsCache);
        videoStorageService.injectVideoAccessService(videoAccessService);
        videoStorageService.injectFfmpegService(ffmpegService);
        videoStorageService.injectAppProperties(appProperties);
        videoStorageService.injectDrive(drive);
        videoStorageService.injectVideoSourceRepository(videoSourceRepository);
    }


    @Test
    void testGetFileRangeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoStorageService.getFileRangeFromDisk("no_full", 0, 10);
        });
    }

    @Test
    void testGetFileSizeFromDisk_FileNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            videoStorageService.getFileSizeFromDisk("no_full");
        });
    }


    @Test
    void testDownloadFileFromFolder_DownloadsAndSavesInfo() throws Exception {
        VideoStorageServiceImpl spyService = spy(videoStorageService);
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
        videoStorageService.downloadFile("id3", dest);
        assertTrue(dest.exists());
        dest.delete();
        dest.getParentFile().delete();
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
        VideoStorageServiceImpl spyService = spy(videoStorageService);
        List<com.google.api.services.drive.model.File> result = spyService.listFilesInFolder(drive, "folderId");
        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getId());
        assertEquals("id2", result.get(1).getId());
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
        List<com.google.api.services.drive.model.File> result = videoStorageService.listFilesInFolder("folder", "2024-01-01T00:00:00Z");
        assertEquals(1, result.size());
        assertEquals("id1", result.getFirst().getId());
    }
}

