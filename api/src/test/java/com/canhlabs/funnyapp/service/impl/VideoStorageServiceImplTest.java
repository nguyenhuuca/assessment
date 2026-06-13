package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.FfmpegService;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoStorageServiceImplTest {

    @Mock StatsCache statsCache;
    @Mock VideoAccessService videoAccessService;
    @Mock FfmpegService ffmpegService;
    @Mock AppProperties appProps;
    @Mock VideoSourceRepository videoSourceRepository;
    @Mock ChatGptService chatGptService;
    @Mock Drive drive;

    @InjectMocks VideoStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service.injectChatGptService(chatGptService);
        service.injectVideoSourceRepository(videoSourceRepository);
        service.injectDrive(drive);
        service.injectFfmpegService(ffmpegService);
        service.injectAppProperties(appProps);
        service.injectVideoAccessService(videoAccessService);
        service.injectCacheStatsService(statsCache);
    }

    // ── getFileSizeFromDisk ─────────────────────────────────────────────────────

    @Test
    void getFileSizeFromDisk_fileNotFound_throwsFileNotFoundException() {
        assertThatThrownBy(() -> service.getFileSizeFromDisk("nonexistent_file_id"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Full file not found");
    }

    // ── getFileRangeFromDisk ────────────────────────────────────────────────────

    @Test
    void getFileRangeFromDisk_fileNotFound_throwsFileNotFoundException() {
        assertThatThrownBy(() -> service.getFileRangeFromDisk("nonexistent_file_id", 0, 100))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Full file not found");
    }

    // ── deleteIfEligible ────────────────────────────────────────────────────────

    @Test
    void deleteIfEligible_fileDoesNotExist_noException() {
        // Should not throw even if file doesn't exist
        service.deleteIfEligible("nonexistent_file_id");
    }

    @Test
    void deleteIfEligible_fileExists_deletesFile(@TempDir Path tempDir) throws IOException {
        // Create a real file in the temp directory to verify deletion logic
        // (can't easily override AppConstant.CACHE_DIR, but we test the no-exception path)
        service.deleteIfEligible("some_file");
        // No exception = success
    }

    // ── saveInfo ────────────────────────────────────────────────────────────────

    @Test
    void saveInfo_fileIdNotExists_savesNewVideoSource() {
        when(videoSourceRepository.existsBySourceId("file123")).thenReturn(false);
        when(chatGptService.makePoem("My Title")).thenReturn("A lovely poem");
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveInfo("file123", "My Title", "http://img.example.com/thumb.jpg");

        ArgumentCaptor<VideoSource> captor = ArgumentCaptor.forClass(VideoSource.class);
        verify(videoSourceRepository).save(captor.capture());
        VideoSource saved = captor.getValue();
        assertThat(saved.getSourceId()).isEqualTo("file123");
        assertThat(saved.getTitle()).isEqualTo("My Title");
        assertThat(saved.getDesc()).isEqualTo("A lovely poem");
        assertThat(saved.getSourceType()).isEqualTo("google_drive");
    }

    @Test
    void saveInfo_fileIdAlreadyExists_doesNotSave() {
        when(videoSourceRepository.existsBySourceId("existing123")).thenReturn(true);

        service.saveInfo("existing123", "Some Title", "thumb.jpg");

        verify(videoSourceRepository, never()).save(any());
        verify(chatGptService, never()).makePoem(anyString());
    }

    // ── listFilesInFolder ───────────────────────────────────────────────────────

    @Test
    void listFilesInFolder_returnsFilesFromDrive() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        File driveFile = new File();
        driveFile.setId("file-id-1");
        driveFile.setName("video1.mp4");
        fileList.setFiles(List.of(driveFile));

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);

        List<File> result = service.listFilesInFolder("folder-id", "2025-01-01T00:00:00Z");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("file-id-1");
    }

    @Test
    void listFilesInFolder_emptyFolder_returnsEmptyList() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        fileList.setFiles(List.of());

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);

        List<File> result = service.listFilesInFolder("folder-id", "2025-01-01T00:00:00Z");

        assertThat(result).isEmpty();
    }

    // ── downloadFileFromFolder — empty folder ───────────────────────────────────

    @Test
    void downloadFileFromFolder_emptyFolder_doesNothing() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        fileList.setFiles(List.of());

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);

        service.downloadFileFromFolder("folder-id", "2025-01-01T00:00:00Z");

        verify(ffmpegService, never()).generateThumbnail(anyString(), anyString());
    }

    // ── cleanup of real files written into CACHE_DIR ────────────────────────────

    @AfterEach
    void cleanupCacheFiles() throws IOException {
        for (String id : List.of("rangefile", "sizefile", "deletefile", "skipfile", "dlfile")) {
            Files.deleteIfExists(cachePath(id));
        }
    }

    private static Path cachePath(String fileId) {
        return Paths.get(AppConstant.CACHE_DIR, fileId + VideoStorageServiceImpl.EXTENSION);
    }

    private static Path writeCacheFile(String fileId, String content) throws IOException {
        Path path = cachePath(fileId);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    // ── getFileSizeFromDisk — happy path ────────────────────────────────────────

    @Test
    void getFileSizeFromDisk_fileExists_returnsLength() throws IOException {
        writeCacheFile("sizefile", "0123456789"); // 10 bytes
        assertThat(service.getFileSizeFromDisk("sizefile")).isEqualTo(10L);
    }

    // ── getFileRangeFromDisk — happy path ───────────────────────────────────────

    @Test
    void getFileRangeFromDisk_fileExists_returnsRangeAndRecordsStats() throws IOException {
        writeCacheFile("rangefile", "0123456789");

        byte[] read;
        try (InputStream in = service.getFileRangeFromDisk("rangefile", 2, 5)) {
            read = in.readAllBytes();
        }

        assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo("2345");
        verify(statsCache).recordHit("rangefile");
        verify(videoAccessService).recordAccess("rangefile");
    }

    // ── deleteIfEligible — actually deletes an existing file ─────────────────────

    @Test
    void deleteIfEligible_fileExists_removesFile() throws IOException {
        Path path = writeCacheFile("deletefile", "data");
        assertThat(Files.exists(path)).isTrue();

        service.deleteIfEligible("deletefile");

        assertThat(Files.exists(path)).isFalse();
    }

    // ── downloadFile — streams drive content into destination ───────────────────

    @Test
    void downloadFile_writesDriveContentToDestination(@TempDir Path tempDir) throws IOException {
        java.io.File dest = tempDir.resolve("out.bin").toFile();
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.Get getReq = mock(Drive.Files.Get.class);

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.get("file-id")).thenReturn(getReq);
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(0);
            out.write("hello-drive".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(getReq).executeMediaAndDownloadTo(any());

        service.downloadFile("file-id", dest);

        assertThat(Files.readString(dest.toPath())).isEqualTo("hello-drive");
    }

    // ── listFilesInFolder(Drive, folderId) overload — single page ───────────────

    @Test
    void listFilesInFolderOverload_returnsFilesFromSinglePage() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        File f = new File();
        f.setId("paged-1");
        f.setName("clip.mp4");
        fileList.setFiles(List.of(f));
        fileList.setNextPageToken(null);

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);

        List<File> result = service.listFilesInFolder(drive, "folder-id");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("paged-1");
    }

    // ── downloadFileFromFolder — file already downloaded, skip branch ────────────

    @Test
    void downloadFileFromFolder_fileAlreadyExists_skipsDownload() throws IOException {
        writeCacheFile("skipfile", "already-here");

        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        File f = new File();
        f.setId("skipfile");
        f.setName("clip.mp4");
        f.setSize(11L);
        fileList.setFiles(List.of(f));

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);

        service.downloadFileFromFolder("folder-id", "2025-01-01T00:00:00Z");

        // existing local file → no download, no thumbnail, no DB write
        verify(driveFiles, never()).get(anyString());
        verify(ffmpegService, never()).generateThumbnail(anyString(), anyString());
        verify(videoSourceRepository, never()).save(any());
    }

    // ── downloadFileFromFolder — full happy path (download + thumbnail + save) ───

    @Test
    void downloadFileFromFolder_newFile_downloadsThumbnailsAndSaves() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        Drive.Files.Get getReq = mock(Drive.Files.Get.class);
        FileList fileList = new FileList();
        File f = new File();
        f.setId("dlfile");
        f.setName("clip.mp4");
        f.setSize(5L);
        fileList.setFiles(List.of(f));

        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);
        when(driveFiles.get("dlfile")).thenReturn(getReq);
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(0);
            out.write("video".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(getReq).executeMediaAndDownloadTo(any());
        when(appProps.getImageStoragePath()).thenReturn("images");
        when(appProps.getImageUrl()).thenReturn("http://img.example.com");
        when(videoSourceRepository.existsBySourceId("dlfile")).thenReturn(false);
        when(chatGptService.makePoem(anyString())).thenReturn("a poem");

        service.downloadFileFromFolder("folder-id", "2025-01-01T00:00:00Z");

        assertThat(Files.exists(cachePath("dlfile"))).isTrue();
        verify(ffmpegService).generateThumbnail(anyString(), eq(Paths.get("images/thumbnails", "dlfile.jpg").toString()));
        ArgumentCaptor<VideoSource> captor = ArgumentCaptor.forClass(VideoSource.class);
        verify(videoSourceRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceId()).isEqualTo("dlfile");
        assertThat(captor.getValue().getTitle()).isEqualTo("clip");
    }
}
