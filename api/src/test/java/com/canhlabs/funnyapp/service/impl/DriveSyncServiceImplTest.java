package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.dto.admin.DriveSyncFileDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.DriveSyncItemStatus;
import com.canhlabs.funnyapp.enums.DriveSyncState;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.InsufficientDiskSpaceException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.DriveSyncService;
import com.canhlabs.funnyapp.service.DriveVideoDownloader;
import com.canhlabs.funnyapp.service.VideoStorageService;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveSyncServiceImplTest {

    @TempDir Path cacheDir;

    Drive drive = mock(Drive.class);
    VideoSourceRepository repo = mock(VideoSourceRepository.class);
    VideoStorageService storage = mock(VideoStorageService.class);
    DriveVideoDownloader downloader = mock(DriveVideoDownloader.class);
    DriveFileLocks locks = new DriveFileLocks();
    /** Runs are queued here and executed by the test, so "active" can be observed. */
    List<Runnable> queued = new ArrayList<>();

    DriveSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DriveSyncServiceImpl(drive, repo, storage, downloader, locks, queued::add, cacheDir, "folder");
    }

    private static File driveFile(String id, String name, Long size) {
        File f = new File();
        f.setId(id);
        f.setName(name);
        f.setSize(size);
        f.setCreatedTime(new DateTime(1_700_000_000_000L));
        return f;
    }

    private static final String BASE_Q = "'folder' in parents and trashed = false";

    private void folderHas(File... files) throws IOException {
        folderHas(BASE_Q, files);
    }

    private void folderHas(String q, File... files) throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        Drive.Files.List list = mock(Drive.Files.List.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(list);
        when(list.setQ(q)).thenReturn(list);
        when(list.setFields(anyString())).thenReturn(list);
        when(list.setPageSize(any())).thenReturn(list);
        when(list.execute()).thenReturn(new FileList().setFiles(List.of(files)));
    }

    private void local(String id, int bytes) throws IOException {
        Files.write(cacheDir.resolve(id + ".full"), new byte[bytes]);
    }

    private static VideoSource row(long id, String sourceId) {
        return VideoSource.builder().id(id).sourceId(sourceId).title("db " + sourceId).build();
    }

    private void runQueued() {
        List<Runnable> toRun = new ArrayList<>(queued);
        queued.clear();
        toRun.forEach(Runnable::run);
    }

    // ── listing ──────────────────────────────────────────────────────────────

    @Test
    void listFolder_reportsStateOfEachFileAndOrphans() throws IOException {
        folderHas(driveFile("synced", "a.mp4", 5L), driveFile("noLocal", "b.mp4", 5L),
                driveFile("noDb", "c.mp4", 5L), driveFile("none", "d.mp4", 5L),
                driveFile("partial", "e.mp4", 5L), driveFile("busy", "f.mp4", 5L));
        local("synced", 5);
        local("noDb", 5);
        local("partial", 2);
        when(repo.findAllBySourceType("google_drive")).thenReturn(List.of(
                row(1, "synced"), row(2, "noLocal"), row(5, "partial"), row(9, "gone")));
        locks.tryClaim("busy");

        DriveSyncListingDto listing = service.listFolder(null, null);

        Map<String, DriveSyncFileDto> byId = listing.getFiles().stream()
                .collect(Collectors.toMap(DriveSyncFileDto::getFileId, Function.identity()));
        assertThat(byId.get("synced").getState()).isEqualTo(DriveSyncState.SYNCED);
        assertThat(byId.get("synced").getVideoSourceId()).isEqualTo(1L);
        assertThat(byId.get("synced").getDbTitle()).isEqualTo("db synced");
        assertThat(byId.get("noLocal").getState()).isEqualTo(DriveSyncState.MISSING_LOCAL);
        assertThat(byId.get("noDb").getState()).isEqualTo(DriveSyncState.MISSING_DB);
        assertThat(byId.get("none").getState()).isEqualTo(DriveSyncState.MISSING_BOTH);
        assertThat(byId.get("partial").getState()).isEqualTo(DriveSyncState.SIZE_MISMATCH);
        assertThat(byId.get("partial").getLocalSizeBytes()).isEqualTo(2L);
        assertThat(byId.get("busy").getState()).isEqualTo(DriveSyncState.SYNCING);
        assertThat(listing.getOrphans()).singleElement()
                .satisfies(o -> assertThat(o.getSourceId()).isEqualTo("gone"));
        assertThat(listing.getMaxBatch()).isEqualTo(DriveSyncService.MAX_BATCH);
    }

    @Test
    void listFolder_driveDown_returns502() throws IOException {
        Drive.Files driveFiles = mock(Drive.Files.class);
        when(drive.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenThrow(new IOException("down"));

        assertThatThrownBy(() -> service.listFolder(null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void listFolder_noRange_computesOrphansAndLoadsWholeTable() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L));
        when(repo.findAllBySourceType("google_drive")).thenReturn(List.of(row(1, "a"), row(9, "gone")));

        DriveSyncListingDto listing = service.listFolder(null, null);

        assertThat(listing.isOrphansChecked()).isTrue();
        assertThat(listing.getOrphans()).extracting(o -> o.getSourceId()).containsExactly("gone");
        verify(repo, never()).findAllBySourceTypeAndSourceIdIn(anyString(), any());
    }

    private static final Instant FROM = Instant.parse("2026-10-18T00:00:00.789Z");
    private static final Instant TO = Instant.parse("2026-10-18T12:01:00Z");

    @Test
    void listFolder_fromOnly_filtersByCreatedTimeAndSkipsOrphans() throws IOException {
        folderHas(BASE_Q + " and createdTime >= '2026-10-18T00:00:00Z'", driveFile("a", "a.mp4", 5L));
        when(repo.findAllBySourceTypeAndSourceIdIn("google_drive", List.of("a"))).thenReturn(List.of(row(1, "a")));

        DriveSyncListingDto listing = service.listFolder(FROM, null);

        assertThat(listing.isOrphansChecked()).isFalse();
        assertThat(listing.getOrphans()).isEmpty();
        assertThat(listing.getFiles()).singleElement().satisfies(f -> {
            assertThat(f.getVideoSourceId()).isEqualTo(1L);
            assertThat(f.getState()).isEqualTo(DriveSyncState.MISSING_LOCAL);
        });
        verify(repo, never()).findAllBySourceType(anyString());
    }

    @Test
    void listFolder_toOnly_usesExclusiveUpperBound() throws IOException {
        folderHas(BASE_Q + " and createdTime < '2026-10-18T12:01:00Z'", driveFile("a", "a.mp4", 5L));
        when(repo.findAllBySourceTypeAndSourceIdIn(anyString(), any())).thenReturn(List.of());

        DriveSyncListingDto listing = service.listFolder(null, TO);

        assertThat(listing.getFiles()).singleElement()
                .satisfies(f -> assertThat(f.getState()).isEqualTo(DriveSyncState.MISSING_BOTH));
        assertThat(listing.isOrphansChecked()).isFalse();
    }

    @Test
    void listFolder_fromAndTo_bothBoundsInQuery() throws IOException {
        folderHas(BASE_Q + " and createdTime >= '2026-10-18T00:00:00Z' and createdTime < '2026-10-18T12:01:00Z'",
                driveFile("a", "a.mp4", 5L));
        when(repo.findAllBySourceTypeAndSourceIdIn(anyString(), any())).thenReturn(List.of());

        assertThat(service.listFolder(FROM, TO).getFiles()).hasSize(1);
    }

    @Test
    void listFolder_rangeWithNoFiles_makesNoDbCall() throws IOException {
        folderHas(BASE_Q + " and createdTime >= '2026-10-18T00:00:00Z'");

        DriveSyncListingDto listing = service.listFolder(FROM, null);

        assertThat(listing.getFiles()).isEmpty();
        assertThat(listing.isOrphansChecked()).isFalse();
        verifyNoInteractions(repo);
    }

    @Test
    void listFolder_rangeWithManyFiles_looksUpRowsInChunksOf1000() throws IOException {
        File[] many = IntStream.range(0, 2500).mapToObj(i -> driveFile("f" + i, "n" + i, 5L)).toArray(File[]::new);
        folderHas(BASE_Q + " and createdTime >= '2026-10-18T00:00:00Z'", many);
        when(repo.findAllBySourceTypeAndSourceIdIn(anyString(), any())).thenReturn(List.of());

        service.listFolder(FROM, null);

        verify(repo, times(2)).findAllBySourceTypeAndSourceIdIn(eq("google_drive"), argThat(c -> c.size() == 1000));
        verify(repo).findAllBySourceTypeAndSourceIdIn(eq("google_drive"), argThat(c -> c.size() == 500));
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void startRun_empty_rejected() {
        assertThatThrownBy(() -> service.startRun(List.of()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessage()).isEqualTo("EMPTY_FILE_IDS"));
        assertThatThrownBy(() -> service.startRun(null)).isInstanceOf(CustomException.class);
    }

    @Test
    void startRun_overMaxBatch_rejected() {
        List<String> ids = IntStream.rangeClosed(0, DriveSyncService.MAX_BATCH).mapToObj(i -> "f" + i).toList();

        assertThatThrownBy(() -> service.startRun(ids))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessage()).isEqualTo("TOO_MANY_FILES"));
    }

    @Test
    void startRun_idOutsideFolder_rejectedAndNothingRuns() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L));

        assertThatThrownBy(() -> service.startRun(List.of("a", "someone-elses-file")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("NOT_IN_FOLDER");
                });
        assertThat(queued).isEmpty();
        assertThat(service.currentRun()).isNull();
    }

    @Test
    void startRun_whileAnotherIsActive_conflict() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L));
        DriveSyncRunDto first = service.startRun(List.of("a"));
        assertThat(first.isActive()).isTrue();

        assertThatThrownBy(() -> service.startRun(List.of("a")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("SYNC_RUNNING");
                });

        runQueued();
        assertThat(service.startRun(List.of("a")).getRunId()).isNotEqualTo(first.getRunId());
    }

    // ── run ──────────────────────────────────────────────────────────────────

    @Test
    void run_downloadsAndUpsertsEachFile() throws IOException {
        folderHas(driveFile("a", "Clip A.mp4", 5L), driveFile("b", "Clip B.mov", null));
        when(downloader.download(eq("a"), eq(5L), any())).thenAnswer(inv -> {
            inv.<java.util.function.LongConsumer>getArgument(2).accept(5L);
            return "thumb-a";
        });
        when(downloader.download(eq("b"), eq(null), any())).thenReturn("thumb-b");

        DriveSyncRunDto started = service.startRun(List.of("a", "b", "a"));
        assertThat(started.getItems()).extracting("status")
                .containsExactly(DriveSyncItemStatus.QUEUED, DriveSyncItemStatus.QUEUED);
        runQueued();

        verify(storage).upsertInfo("a", "Clip A", "thumb-a");
        verify(storage).upsertInfo("b", "Clip B", "thumb-b");
        DriveSyncRunDto done = service.currentRun();
        assertThat(done.isActive()).isFalse();
        assertThat(done.getFinishedAt()).isNotNull();
        assertThat(done.getItems()).extracting("status")
                .containsExactly(DriveSyncItemStatus.DONE, DriveSyncItemStatus.DONE);
        assertThat(done.getItems().get(0).getDownloadedBytes()).isEqualTo(5L);
        assertThat(locks.isHeld("a")).isFalse();
        assertThat(locks.isHeld("b")).isFalse();
    }

    @Test
    void run_fileHeldByCron_isSkipped() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L));
        locks.tryClaim("a");

        service.startRun(List.of("a"));
        runQueued();

        verify(downloader, never()).download(anyString(), any(), any());
        assertThat(service.currentRun().getItems().get(0).getStatus()).isEqualTo(DriveSyncItemStatus.SKIPPED_BUSY);
        assertThat(locks.isHeld("a")).isTrue(); // the cron still owns it
    }

    @Test
    void run_oneFailureDoesNotStopTheOthers() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L), driveFile("b", "b.mp4", 5L));
        when(downloader.download(eq("a"), any(), any())).thenThrow(new IOException("drive 500"));
        when(downloader.download(eq("b"), any(), any())).thenReturn("thumb-b");

        service.startRun(List.of("a", "b"));
        runQueued();

        var items = service.currentRun().getItems();
        assertThat(items.get(0).getStatus()).isEqualTo(DriveSyncItemStatus.FAILED);
        assertThat(items.get(0).getErrorCode()).isEqualTo("DRIVE_ERROR");
        assertThat(items.get(1).getStatus()).isEqualTo(DriveSyncItemStatus.DONE);
        verify(storage, never()).upsertInfo(eq("a"), any(), any());
        assertThat(locks.isHeld("a")).isFalse();
    }

    @Test
    void errorCode_mapsFailureKinds() {
        assertThat(DriveSyncServiceImpl.errorCode(new InsufficientDiskSpaceException("x"))).isEqualTo("DISK_FULL");
        assertThat(DriveSyncServiceImpl.errorCode(new IOException("x"))).isEqualTo("DRIVE_ERROR");
        assertThat(DriveSyncServiceImpl.errorCode(new DataIntegrityViolationException("x"))).isEqualTo("DB_ERROR");
        assertThat(DriveSyncServiceImpl.errorCode(new IllegalStateException("x"))).isEqualTo("INTERNAL");
    }

    @Test
    void executorRejects_runIsNotLeftActive() throws IOException {
        folderHas(driveFile("a", "a.mp4", 5L));
        service = new DriveSyncServiceImpl(drive, repo, storage, downloader, locks,
                task -> { throw new IllegalStateException("no threads"); }, cacheDir, "folder");

        assertThatThrownBy(() -> service.startRun(List.of("a"))).isInstanceOf(IllegalStateException.class);
        assertThat(service.currentRun().isActive()).isFalse();
    }
}
