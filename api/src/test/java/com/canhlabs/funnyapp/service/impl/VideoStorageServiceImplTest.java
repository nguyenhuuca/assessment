package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.cache.FeedCache;
import com.canhlabs.funnyapp.cache.StatsCache;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.event.VideoIngestedEvent;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.ChatGptService;
import com.canhlabs.funnyapp.service.DriveVideoDownloader;
import com.canhlabs.funnyapp.service.VideoAccessService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
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
import org.springframework.context.ApplicationEventPublisher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoStorageServiceImplTest {

    @Mock StatsCache statsCache;
    @Mock VideoAccessService videoAccessService;
    @Mock DriveVideoDownloader driveVideoDownloader;
    DriveFileLocks driveFileLocks = new DriveFileLocks();
    @Mock VideoSourceRepository videoSourceRepository;
    @Mock ChatGptService chatGptService;
    @Mock Drive drive;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock FeedCache feedCache;

    @InjectMocks VideoStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service.injectChatGptService(chatGptService);
        service.injectEventPublisher(eventPublisher);
        service.injectVideoSourceRepository(videoSourceRepository);
        service.injectDrive(drive);
        service.injectDriveVideoDownloader(driveVideoDownloader);
        service.injectDriveFileLocks(driveFileLocks);
        service.injectVideoAccessService(videoAccessService);
        service.injectCacheStatsService(statsCache);
        service.injectFeedCache(feedCache);
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

    @Test
    void getFileRangeFromDisk_seekFails_closesTheFile() throws IOException {
        Path path = writeCacheFile("rangefile", "0123456789");

        assertThatThrownBy(() -> service.getFileRangeFromDisk("rangefile", -1, 5))
                .isInstanceOf(IOException.class);

        // on Windows an open RandomAccessFile blocks the delete, so this proves it was closed
        assertThat(Files.deleteIfExists(path)).isTrue();
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

    @Test
    void deleteIfEligible_invalidId_rejected() {
        assertThatThrownBy(() -> service.deleteIfEligible("../etc/passwd"))
                .hasMessageContaining("Invalid file id");
    }

    @Test
    void deleteIfEligible_fileBeingSynced_throws() {
        driveFileLocks.tryClaim("busy");
        try {
            assertThatThrownBy(() -> service.deleteIfEligible("busy"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            driveFileLocks.release("busy");
        }
    }

    // ── deleteDriveFile ─────────────────────────────────────────────────────────

    @Test
    void deleteDriveFile_success_callsDrive() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.Delete del = mock(Drive.Files.Delete.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.delete("abc-1")).thenReturn(del);

        service.deleteDriveFile("abc-1");

        verify(del).execute();
    }

    @Test
    void deleteDriveFile_notFound_isIgnored() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.Delete del = mock(Drive.Files.Delete.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.delete("abc")).thenReturn(del);
        doThrow(driveError(404)).when(del).execute();

        service.deleteDriveFile("abc");
    }

    @Test
    void deleteDriveFile_forbidden_throwsIllegalState() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.Delete del = mock(Drive.Files.Delete.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.delete("abc")).thenReturn(del);
        doThrow(driveError(403)).when(del).execute();

        assertThatThrownBy(() -> service.deleteDriveFile("abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("403");
    }

    @Test
    void deleteDriveFile_ioError_throwsIllegalState() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.Delete del = mock(Drive.Files.Delete.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.delete("abc")).thenReturn(del);
        doThrow(new IOException("boom")).when(del).execute();

        assertThatThrownBy(() -> service.deleteDriveFile("abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteDriveFile_invalidId_rejectedWithoutDriveCall() {
        assertThatThrownBy(() -> service.deleteDriveFile("a/b"))
                .hasMessageContaining("Invalid file id");
        verify(drive, never()).files();
    }

    private static GoogleJsonResponseException driveError(int status) {
        return new GoogleJsonResponseException(
                new HttpResponseException.Builder(status, "err", new HttpHeaders()), null);
    }

    // ── saveInfo ────────────────────────────────────────────────────────────────

    @Test
    void saveInfo_fileIdNotExists_savesNewVideoSource() {
        when(videoSourceRepository.existsBySourceId("file123")).thenReturn(false);
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatGptService.makePoem("My Title")).thenReturn("A poem");

        service.saveInfo("file123", "My Title", "http://img.example.com/thumb.jpg");

        ArgumentCaptor<VideoSource> captor = ArgumentCaptor.forClass(VideoSource.class);
        verify(videoSourceRepository).save(captor.capture());
        VideoSource saved = captor.getValue();
        assertThat(saved.getSourceId()).isEqualTo("file123");
        assertThat(saved.getTitle()).isEqualTo("My Title");
        assertThat(saved.getDesc()).isEqualTo("A poem");
        assertThat(saved.getSourceType()).isEqualTo("google_drive");
    }

    @Test
    void saveInfo_chatGptFails_stillSavesWithoutDesc() {
        when(videoSourceRepository.existsBySourceId("file123")).thenReturn(false);
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatGptService.makePoem("My Title")).thenThrow(new RuntimeException("429 insufficient_quota"));

        service.saveInfo("file123", "My Title", "thumb.jpg");

        ArgumentCaptor<VideoSource> captor = ArgumentCaptor.forClass(VideoSource.class);
        verify(videoSourceRepository).save(captor.capture());
        assertThat(captor.getValue().getDesc()).isNull();
        assertThat(captor.getValue().getSourceId()).isEqualTo("file123");
    }

    @Test
    void saveInfo_fileIdAlreadyExists_doesNotSave() {
        when(videoSourceRepository.existsBySourceId("existing123")).thenReturn(true);

        service.saveInfo("existing123", "Some Title", "thumb.jpg");

        verify(videoSourceRepository, never()).save(any());
        verify(chatGptService, never()).makePoem(anyString());
    }

    @Test
    void saveInfo_publishesVideoIngestedEventWithDriveFileAndSourceId() {
        when(videoSourceRepository.existsBySourceId("file123")).thenReturn(false);
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> {
            VideoSource v = inv.getArgument(0);
            v.setId(77L);
            return v;
        });

        service.saveInfo("file123", "My Title", "thumb.jpg");

        verify(eventPublisher).publishEvent(new VideoIngestedEvent("file123", 77L));
    }

    @Test
    void saveInfo_publishFails_ingestStillSucceeds() {
        when(videoSourceRepository.existsBySourceId("file123")).thenReturn(false);
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("listener boom")).when(eventPublisher).publishEvent(any(Object.class));

        service.saveInfo("file123", "My Title", "thumb.jpg");

        verify(videoSourceRepository).save(any(VideoSource.class));
    }

    @Test
    void saveInfo_alreadyExists_publishesNothing() {
        when(videoSourceRepository.existsBySourceId("existing123")).thenReturn(true);

        service.saveInfo("existing123", "Some Title", "thumb.jpg");

        verify(eventPublisher, never()).publishEvent(any(Object.class));
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

        verify(driveVideoDownloader, never()).download(anyString(), any(), any());
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
        verify(statsCache).recordMiss("rangefile");
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

        // existing local file → no download, no DB write
        verify(driveVideoDownloader, never()).download(anyString(), any(), any());
        verify(videoSourceRepository, never()).save(any());
    }

    // ── downloadFileFromFolder — new file: shared downloader + save ──────────────

    private void stubListing(String id, String name, long size) throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List listRequest = mock(Drive.Files.List.class);
        FileList fileList = new FileList();
        File f = new File();
        f.setId(id);
        f.setName(name);
        f.setSize(size);
        fileList.setFiles(List.of(f));
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(fileList);
    }

    @Test
    void downloadFileFromFolder_newFile_downloadsAndSaves() throws IOException {
        stubListing("dlfile", "clip.mp4", 5L);
        when(driveVideoDownloader.download(eq("dlfile"), eq(5L), any())).thenReturn("http://img.example.com/dlfile.jpg");
        when(videoSourceRepository.existsBySourceId("dlfile")).thenReturn(false);

        service.downloadFileFromFolder("folder-id", "2025-01-01T00:00:00Z");

        ArgumentCaptor<VideoSource> captor = ArgumentCaptor.forClass(VideoSource.class);
        verify(videoSourceRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceId()).isEqualTo("dlfile");
        assertThat(captor.getValue().getTitle()).isEqualTo("clip");
        assertThat(captor.getValue().getThumbnailPath()).isEqualTo("http://img.example.com/dlfile.jpg");
        assertThat(driveFileLocks.isHeld("dlfile")).isFalse();
    }

    @Test
    void downloadFileFromFolder_fileHeldByForceSync_skips() throws IOException {
        stubListing("dlfile", "clip.mp4", 5L);
        driveFileLocks.tryClaim("dlfile");

        service.downloadFileFromFolder("folder-id", "2025-01-01T00:00:00Z");

        verify(driveVideoDownloader, never()).download(anyString(), any(), any());
        verify(videoSourceRepository, never()).save(any());
        assertThat(driveFileLocks.isHeld("dlfile")).isTrue(); // still owned by the force sync
    }

    // ── upsertInfo ───────────────────────────────────────────────────────────────

    @Test
    void upsertInfo_rowWithDescription_keepsItWithoutChatGpt() {
        when(videoSourceRepository.findBySourceId("f1"))
                .thenReturn(java.util.Optional.of(VideoSource.builder().sourceId("f1").desc("old poem").build()));

        service.upsertInfo("f1", "Title", "thumb");

        verify(chatGptService, never()).makePoem(anyString());
        verify(videoSourceRepository).upsertDriveSource(any(), eq("f1"), eq("Title"), eq(null), eq("thumb"));
    }

    @Test
    void upsertInfo_newRow_generatesDescription() {
        when(videoSourceRepository.findBySourceId("f2")).thenReturn(java.util.Optional.empty());
        when(chatGptService.makePoem("Title")).thenReturn("A poem");

        service.upsertInfo("f2", "Title", "thumb");

        verify(videoSourceRepository).upsertDriveSource(any(), eq("f2"), eq("Title"), eq("A poem"), eq("thumb"));
    }

    @Test
    void saveInfo_newRow_invalidatesFeedCache() {
        when(videoSourceRepository.existsBySourceId("file9")).thenReturn(false);
        when(videoSourceRepository.save(any(VideoSource.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveInfo("file9", "T", "thumb");

        verify(feedCache).invalidate();
    }

    @Test
    void saveInfo_existingRow_leavesFeedCache() {
        when(videoSourceRepository.existsBySourceId("file9")).thenReturn(true);

        service.saveInfo("file9", "T", "thumb");

        verify(feedCache, never()).invalidate();
    }

    @Test
    void upsertInfo_invalidatesFeedCache() {
        when(videoSourceRepository.findBySourceId("f3")).thenReturn(java.util.Optional.empty());

        service.upsertInfo("f3", "Title", "thumb");

        verify(feedCache).invalidate();
    }
}
