package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.dto.admin.DriveSyncFileDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncItemDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncOrphanDto;
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
import com.canhlabs.funnyapp.utils.AppConstant;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DriveSyncServiceImpl implements DriveSyncService {

    static final String SOURCE_TYPE = "google_drive";

    private final Drive drive;
    private final VideoSourceRepository videoSourceRepository;
    private final VideoStorageService videoStorageService;
    private final DriveVideoDownloader downloader;
    private final DriveFileLocks locks;
    private final Executor executor;
    private final Path cacheDir;
    private final String folderId;

    /** The running run, or the last finished one. Only replaced when no run is active. */
    private final AtomicReference<RunState> current = new AtomicReference<>();

    @Autowired
    public DriveSyncServiceImpl(Drive drive, VideoSourceRepository videoSourceRepository,
                                VideoStorageService videoStorageService, DriveVideoDownloader downloader,
                                DriveFileLocks locks) {
        this(drive, videoSourceRepository, videoStorageService, downloader, locks,
                task -> Thread.ofVirtual().name("drive-sync").start(task),
                Paths.get(AppConstant.CACHE_DIR), AppConstant.FOLDER_ID);
    }

    DriveSyncServiceImpl(Drive drive, VideoSourceRepository videoSourceRepository,
                         VideoStorageService videoStorageService, DriveVideoDownloader downloader,
                         DriveFileLocks locks, Executor executor, Path cacheDir, String folderId) {
        this.drive = drive;
        this.videoSourceRepository = videoSourceRepository;
        this.videoStorageService = videoStorageService;
        this.downloader = downloader;
        this.locks = locks;
        this.executor = executor;
        this.cacheDir = cacheDir;
        this.folderId = folderId;
    }

    @Override
    public DriveSyncListingDto listFolder() {
        List<File> driveFiles = listDriveFolder();
        Map<String, VideoSource> rows = videoSourceRepository.findAllBySourceType(SOURCE_TYPE).stream()
                .collect(Collectors.toMap(VideoSource::getSourceId, Function.identity(), (a, b) -> a));

        List<DriveSyncFileDto> files = new ArrayList<>(driveFiles.size());
        for (File f : driveFiles) {
            VideoSource row = rows.remove(f.getId());
            Long localSize = localSize(f.getId());
            files.add(DriveSyncFileDto.builder()
                    .fileId(f.getId())
                    .name(f.getName())
                    .sizeBytes(f.getSize())
                    .createdAt(f.getCreatedTime() == null ? null : Instant.ofEpochMilli(f.getCreatedTime().getValue()))
                    .localSizeBytes(localSize)
                    .videoSourceId(row == null ? null : row.getId())
                    .dbTitle(row == null ? null : row.getTitle())
                    .state(stateOf(f, localSize, row != null))
                    .build());
        }
        // rows left over have no file in the Drive folder any more
        List<DriveSyncOrphanDto> orphans = rows.values().stream()
                .map(r -> DriveSyncOrphanDto.builder()
                        .videoSourceId(r.getId()).sourceId(r.getSourceId()).title(r.getTitle()).build())
                .toList();
        return DriveSyncListingDto.builder().files(files).orphans(orphans).maxBatch(MAX_BATCH).build();
    }

    @Override
    public DriveSyncRunDto startRun(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, 4201, "EMPTY_FILE_IDS");
        }
        Set<String> ids = new LinkedHashSet<>(fileIds);
        if (ids.size() > MAX_BATCH) {
            throw error(HttpStatus.BAD_REQUEST, 4202, "TOO_MANY_FILES");
        }
        RunState previous = current.get();
        if (previous != null && previous.isActive()) {
            throw error(HttpStatus.CONFLICT, 4093, "SYNC_RUNNING");
        }

        // only files of the video folder: the Drive account can read far more than this folder
        Map<String, File> inFolder = listDriveFolder().stream()
                .collect(Collectors.toMap(File::getId, Function.identity(), (a, b) -> a));
        if (!inFolder.keySet().containsAll(ids)) {
            throw error(HttpStatus.BAD_REQUEST, 4203, "NOT_IN_FOLDER");
        }

        Map<String, RunItem> items = new LinkedHashMap<>();
        for (String id : ids) {
            File f = inFolder.get(id);
            items.put(id, new RunItem(id, f.getName(), f.getSize() == null ? 0 : f.getSize()));
        }
        RunState run = new RunState(UUID.randomUUID().toString(), Instant.now(), List.copyOf(items.values()));
        // compare-and-set: two clicks at the same time start one run
        if (!current.compareAndSet(previous, run)) {
            throw error(HttpStatus.CONFLICT, 4093, "SYNC_RUNNING");
        }
        log.info("Drive force sync {} started for {} file(s)", run.runId, ids.size());
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException e) {
            run.finishedAt = Instant.now(); // never leave a run that cannot finish marked active
            throw e;
        }
        return run.snapshot();
    }

    @Override
    public DriveSyncRunDto currentRun() {
        RunState run = current.get();
        return run == null ? null : run.snapshot();
    }

    void execute(RunState run) {
        try {
            for (RunItem item : run.items) {
                syncOne(item);
            }
        } finally {
            run.finishedAt = Instant.now();
            log.info("Drive force sync {} finished", run.runId);
        }
    }

    private void syncOne(RunItem item) {
        if (!locks.tryClaim(item.fileId)) {
            item.status = DriveSyncItemStatus.SKIPPED_BUSY;
            return;
        }
        try {
            item.status = DriveSyncItemStatus.DOWNLOADING;
            String thumbnailUrl = downloader.download(item.fileId, item.totalBytes == 0 ? null : item.totalBytes,
                    bytes -> item.downloadedBytes = bytes);
            item.status = DriveSyncItemStatus.SAVING;
            videoStorageService.upsertInfo(item.fileId, DriveVideoDownloader.titleOf(item.name), thumbnailUrl);
            item.status = DriveSyncItemStatus.DONE;
        } catch (Exception e) {
            item.errorCode = errorCode(e);
            item.status = DriveSyncItemStatus.FAILED;
            log.error("Drive force sync failed for {}: {} {}", item.fileId, item.errorCode, e.getMessage());
        } finally {
            locks.release(item.fileId);
        }
    }

    static String errorCode(Exception e) {
        if (e instanceof InsufficientDiskSpaceException) return "DISK_FULL";
        if (e instanceof IOException || e instanceof UncheckedIOException) return "DRIVE_ERROR";
        if (e instanceof DataAccessException) return "DB_ERROR";
        return "INTERNAL";
    }

    private DriveSyncState stateOf(File f, Long localSize, boolean hasRow) {
        if (locks.isHeld(f.getId())) return DriveSyncState.SYNCING;
        if (localSize == null) return hasRow ? DriveSyncState.MISSING_LOCAL : DriveSyncState.MISSING_BOTH;
        if (f.getSize() != null && !f.getSize().equals(localSize)) return DriveSyncState.SIZE_MISMATCH;
        return hasRow ? DriveSyncState.SYNCED : DriveSyncState.MISSING_DB;
    }

    private Long localSize(String fileId) {
        Path p = cacheDir.resolve(fileId + VideoStorageServiceImpl.EXTENSION);
        try {
            return Files.exists(p) ? Files.size(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private List<File> listDriveFolder() {
        try {
            List<File> files = new ArrayList<>();
            Drive.Files.List request = drive.files().list()
                    .setQ(String.format("'%s' in parents and trashed = false", folderId))
                    .setFields("nextPageToken, files(id, name, size, createdTime)")
                    .setPageSize(1000);
            do {
                FileList page = request.execute();
                files.addAll(page.getFiles());
                request.setPageToken(page.getNextPageToken());
            } while (request.getPageToken() != null && !request.getPageToken().isEmpty());
            return files;
        } catch (IOException e) {
            log.error("Listing Drive folder failed: {}", e.getMessage());
            throw error(HttpStatus.BAD_GATEWAY, 5021, "DRIVE_UNAVAILABLE");
        }
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }

    /** Written by the run thread, read by polling requests: fields are volatile, snapshots are copies. */
    static final class RunState {
        final String runId;
        final Instant startedAt;
        final List<RunItem> items;
        volatile Instant finishedAt;

        RunState(String runId, Instant startedAt, List<RunItem> items) {
            this.runId = runId;
            this.startedAt = startedAt;
            this.items = items;
        }

        boolean isActive() {
            return finishedAt == null;
        }

        DriveSyncRunDto snapshot() {
            return DriveSyncRunDto.builder()
                    .runId(runId)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .active(isActive())
                    .items(items.stream().map(RunItem::snapshot).toList())
                    .build();
        }
    }

    static final class RunItem {
        final String fileId;
        final String name;
        final long totalBytes;
        volatile DriveSyncItemStatus status = DriveSyncItemStatus.QUEUED;
        volatile long downloadedBytes;
        volatile String errorCode;

        RunItem(String fileId, String name, long totalBytes) {
            this.fileId = fileId;
            this.name = name;
            this.totalBytes = totalBytes;
        }

        DriveSyncItemDto snapshot() {
            return DriveSyncItemDto.builder()
                    .fileId(fileId).name(name).status(status)
                    .downloadedBytes(downloadedBytes).totalBytes(totalBytes).errorCode(errorCode)
                    .build();
        }
    }
}
