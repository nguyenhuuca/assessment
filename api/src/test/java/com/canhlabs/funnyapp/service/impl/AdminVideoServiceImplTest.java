package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.DriveFileLocks;
import com.canhlabs.funnyapp.cache.FeedCache;
import com.canhlabs.funnyapp.dto.admin.AdminHlsStatusDto;
import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminVideoServiceImplTest {

    @Mock VideoSourceRepository videoSourceRepository;
    @Mock UserRepo userRepo;
    @Mock VideoCommentRepository commentRepository;
    @Mock VideoStorageService videoStorageService;
    @Mock HlsStorage hlsStorage;
    @Mock HlsTranscodeWorker hlsWorker;
    @Mock FeedCache feedCache;
    DriveFileLocks locks = new DriveFileLocks();

    @InjectMocks AdminVideoServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.injectVideoStorageService(videoStorageService);
        service.injectVideoSourceRepository(videoSourceRepository);
        service.injectUserRepo(userRepo);
        service.injectVideoCommentRepository(commentRepository);
        service.injectHlsStorage(hlsStorage);
        service.injectHlsWorker(hlsWorker);
        service.injectDriveFileLocks(locks);
        service.injectFeedCache(feedCache);
    }

    // ── getVideos ──────────────────────────────────────────────────────────────

    @Test
    void getVideos_statusNull_callsFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(videoSourceRepository.findAll(pageable)).thenReturn(Page.empty());

        service.getVideos(pageable, null);

        verify(videoSourceRepository).findAll(pageable);
        verify(videoSourceRepository, never()).findAllByStatus(any(), any());
    }

    @Test
    void getVideos_statusProvided_callsFindAllByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(videoSourceRepository.findAllByStatus(VideoStatus.FLAGGED, pageable)).thenReturn(Page.empty());

        service.getVideos(pageable, VideoStatus.FLAGGED);

        verify(videoSourceRepository).findAllByStatus(VideoStatus.FLAGGED, pageable);
        verify(videoSourceRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getVideos_mapsSourceToDto() {
        VideoSource source = videoSource(1L, "My Video", "https://thumb.jpg", VideoStatus.PUBLISHED);
        source.setViewCount(42L);
        Pageable pageable = PageRequest.of(0, 20);
        when(videoSourceRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(source)));

        Page<AdminVideoDto> result = service.getVideos(pageable, null);

        assertEquals(1, result.getContent().size());
        AdminVideoDto dto = result.getContent().get(0);
        assertEquals(1L, dto.getId());
        assertEquals("My Video", dto.getTitle());
        assertEquals("https://thumb.jpg", dto.getThumbnailPath());
        assertEquals(VideoStatus.PUBLISHED, dto.getStatus());
        assertEquals(42L, dto.getViewCount());
        assertEquals(0, dto.getPriority());
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_videoFound_setsStatusAndSaves() {
        VideoSource source = videoSource(5L, "Title", null, VideoStatus.PENDING);
        when(videoSourceRepository.findById(5L)).thenReturn(Optional.of(source));

        service.updateStatus(5L, VideoStatus.PUBLISHED);

        assertEquals(VideoStatus.PUBLISHED, source.getStatus());
        verify(videoSourceRepository).save(source);
    }

    @Test
    void updateStatus_videoNotFound_throwsIllegalArgumentException() {
        when(videoSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(99L, VideoStatus.FLAGGED));
        verify(videoSourceRepository, never()).save(any());
    }

    // ── updateHidden ──────────────────────────────────────────────────────────

    @Test
    void updateHidden_videoFound_setsFlagAndSaves() {
        VideoSource source = videoSource(5L, "Title", null, VideoStatus.PUBLISHED);
        when(videoSourceRepository.findById(5L)).thenReturn(Optional.of(source));

        service.updateHidden(5L, true);

        assertTrue(source.isHide());
        verify(videoSourceRepository).save(source);
    }

    @Test
    void updateHidden_videoNotFound_throwsIllegalArgumentException() {
        when(videoSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateHidden(99L, true));
        verify(videoSourceRepository, never()).save(any());
    }

    // ── updatePriority ────────────────────────────────────────────────────────

    @Test
    void updatePriority_videoFound_setsPriorityAndSaves() {
        VideoSource source = videoSource(5L, "Title", null, VideoStatus.PENDING);
        when(videoSourceRepository.findById(5L)).thenReturn(Optional.of(source));

        service.updatePriority(5L, 50);

        assertEquals(50, source.getPriority());
        verify(videoSourceRepository).save(source);
    }

    @Test
    void updatePriority_videoNotFound_throwsIllegalArgumentException() {
        when(videoSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updatePriority(99L, 10));
        verify(videoSourceRepository, never()).save(any());
    }

    @Test
    void updatePriority_negativeValue_throwsAndSkipsLookup() {
        assertThrows(RuntimeException.class, () -> service.updatePriority(5L, -1));
        verify(videoSourceRepository, never()).findById(any());
    }

    @Test
    void updatePriority_aboveMax_throwsAndSkipsLookup() {
        assertThrows(RuntimeException.class, () -> service.updatePriority(5L, 10000));
        verify(videoSourceRepository, never()).findById(any());
    }

    // ── deleteVideo ───────────────────────────────────────────────────────────

    private VideoSource source(String type, String sourceId) {
        VideoSource s = new VideoSource();
        s.setSourceType(type);
        s.setSourceId(sourceId);
        return s;
    }

    @Test
    void deleteVideo_defaults_deletesRowOnly() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));

        service.deleteVideo(7L, false, false);

        verify(videoSourceRepository).deleteById(7L);
        verifyNoInteractions(videoStorageService);
    }

    @Test
    void deleteVideo_localOnly_deletesLocalFile() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));

        service.deleteVideo(7L, true, false);

        verify(videoStorageService).deleteIfEligible("abc");
        verify(videoStorageService, never()).deleteDriveFile(any());
        verify(videoSourceRepository).deleteById(7L);
    }

    @Test
    void deleteVideo_driveOnly_deletesDriveFile() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));

        service.deleteVideo(7L, false, true);

        verify(videoStorageService).deleteDriveFile("abc");
        verify(videoStorageService, never()).deleteIfEligible(any());
        verify(videoSourceRepository).deleteById(7L);
    }

    @Test
    void deleteVideo_nonDriveRow_skipsDrive() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("youtube", "yt1")));

        service.deleteVideo(7L, false, true);

        verify(videoStorageService, never()).deleteDriveFile(any());
        verify(videoSourceRepository).deleteById(7L);
    }

    @Test
    void deleteVideo_driveFailure_keepsRow() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));
        doThrow(new IllegalStateException("403")).when(videoStorageService).deleteDriveFile("abc");

        assertThrows(IllegalStateException.class, () -> service.deleteVideo(7L, true, true));

        verify(videoStorageService, never()).deleteIfEligible(any());
        verify(videoSourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteVideo_unknownId_throws() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteVideo(7L, true, true));

        assertEquals("Video not found: 7", ex.getMessage());
        verify(videoSourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteVideo_alwaysDeletesHlsDirectory_evenWithoutLocalFileFlag() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));

        service.deleteVideo(7L, false, false);

        verify(hlsStorage).delete("abc");
        verify(videoStorageService, never()).deleteIfEligible(any());
        assertFalse(locks.isHeld("abc"));
    }

    @Test
    void deleteVideo_fileLocked_skipsHlsDeleteButRemovesRow() {
        when(videoSourceRepository.findById(7L)).thenReturn(Optional.of(source("google_drive", "abc")));
        locks.tryClaim("abc");

        service.deleteVideo(7L, false, false);

        verify(hlsStorage, never()).delete(any());
        verify(videoSourceRepository).deleteById(7L);
        assertTrue(locks.isHeld("abc")); // the other holder's claim is untouched
    }

    // ── HLS toggles ───────────────────────────────────────────────────────────

    private VideoSource hlsSource(String type, HlsStatus status) {
        VideoSource s = source(type, "abc");
        s.setId(42L);
        s.setHlsStatus(status);
        return s;
    }

    @Test
    void enableHls_noneOrError_becomesPendingClearsErrorAndWakesWorker() {
        for (HlsStatus from : List.of(HlsStatus.NONE, HlsStatus.ERROR)) {
            VideoSource s = hlsSource("google_drive", from);
            s.setHlsError("TIMEOUT");
            when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(s));

            AdminHlsStatusDto dto = service.enableHls(42L);

            assertEquals(HlsStatus.PENDING, dto.getHlsStatus());
            assertNull(dto.getHlsError());
            assertEquals(42L, dto.getId());
            verify(videoSourceRepository).save(s);
            verify(hlsWorker).wakeUp();
            clearInvocations(videoSourceRepository, hlsWorker);
        }
    }

    @Test
    void enableHls_pendingProcessingReady_unchanged() {
        for (HlsStatus from : List.of(HlsStatus.PENDING, HlsStatus.PROCESSING, HlsStatus.READY)) {
            when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(hlsSource("google_drive", from)));

            AdminHlsStatusDto dto = service.enableHls(42L);

            assertEquals(from, dto.getHlsStatus());
        }
        verify(videoSourceRepository, never()).save(any());
        verify(hlsWorker, never()).wakeUp();
    }

    @Test
    void enableHls_nonDrive_throws400() {
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(hlsSource("youtube", HlsStatus.NONE)));

        CustomException ex = assertThrows(CustomException.class, () -> service.enableHls(42L));

        assertEquals(400, ex.getStatus().value());
        assertEquals("error.hls.notDriveVideo", ex.getMessageKey());
        verify(videoSourceRepository, never()).save(any());
    }

    @Test
    void enableHls_unknownId_throws404() {
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.enableHls(42L));

        assertEquals(404, ex.getStatus().value());
        assertEquals("error.video.notFound", ex.getMessageKey());
    }

    @Test
    void disableHls_anyStatus_becomesNoneAndDeletesDirectory() {
        VideoSource s = hlsSource("google_drive", HlsStatus.READY);
        s.setHlsError("x");
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(s));

        AdminHlsStatusDto dto = service.disableHls(42L);

        assertEquals(HlsStatus.NONE, dto.getHlsStatus());
        assertNull(dto.getHlsError());
        verify(videoSourceRepository).save(s);
        verify(hlsStorage).delete("abc");
        assertFalse(locks.isHeld("abc"));
    }

    @Test
    void disableHls_whileProcessing_neverConflicts_leavesCleanupToHolder() {
        VideoSource s = hlsSource("google_drive", HlsStatus.PROCESSING);
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(s));
        locks.tryClaim("abc");

        AdminHlsStatusDto dto = service.disableHls(42L);

        assertEquals(HlsStatus.NONE, dto.getHlsStatus());
        verify(hlsStorage, never()).delete(any());
        assertTrue(locks.isHeld("abc"));
    }

    @Test
    void disableHls_deleteFails_releasesLock() {
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(hlsSource("google_drive", HlsStatus.READY)));
        doThrow(new IllegalStateException("io")).when(hlsStorage).delete("abc");

        assertThrows(IllegalStateException.class, () -> service.disableHls(42L));

        assertFalse(locks.isHeld("abc"));
    }

    @Test
    void disableHls_nonDrive_throws400AndUnknown404() {
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(hlsSource("youtube", HlsStatus.NONE)));
        assertEquals(400, assertThrows(CustomException.class, () -> service.disableHls(42L)).getStatus().value());

        when(videoSourceRepository.findById(42L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(CustomException.class, () -> service.disableHls(42L)).getStatus().value());
    }

    @Test
    void getVideos_hlsStatusNullForNonDrive_andShownForDrive() {
        VideoSource drive = hlsSource("google_drive", HlsStatus.ERROR);
        drive.setHlsError("TIMEOUT");
        VideoSource yt = hlsSource("youtube", HlsStatus.NONE);
        Pageable pageable = PageRequest.of(0, 20);
        when(videoSourceRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(drive, yt)));

        List<AdminVideoDto> out = service.getVideos(pageable, null).getContent();

        assertEquals(HlsStatus.ERROR, out.get(0).getHlsStatus());
        assertEquals("TIMEOUT", out.get(0).getHlsError());
        assertNull(out.get(1).getHlsStatus());
        assertNull(out.get(1).getHlsError());
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsAggregatedCounts() {
        when(videoSourceRepository.count()).thenReturn(100L);
        when(videoSourceRepository.countByStatus(VideoStatus.PENDING)).thenReturn(12L);
        when(videoSourceRepository.countByStatus(VideoStatus.FLAGGED)).thenReturn(5L);
        when(userRepo.count()).thenReturn(42L);
        when(commentRepository.count()).thenReturn(300L);
        when(commentRepository.countByStatus(CommentStatus.REMOVED)).thenReturn(7L);

        AdminStatsDto stats = service.getStats();

        assertEquals(100L, stats.getTotalVideos());
        assertEquals(42L,  stats.getTotalUsers());
        assertEquals(12L,  stats.getPendingCount());
        assertEquals(5L,   stats.getFlaggedCount());
        assertEquals(300L, stats.getTotalComments());
        assertEquals(7L,   stats.getRemovedComments());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static VideoSource videoSource(Long id, String title, String thumbnail, VideoStatus status) {
        VideoSource v = new VideoSource();
        v.setId(id);
        v.setTitle(title);
        v.setThumbnailPath(thumbnail);
        v.setStatus(status);
        return v;
    }

    // ── feed cache eviction ───────────────────────────────────────────────────

    @Test
    void feedMutations_invalidateTheFeedCache() {
        VideoSource s = hlsSource("google_drive", HlsStatus.NONE);
        when(videoSourceRepository.findById(42L)).thenReturn(Optional.of(s));

        service.updateStatus(42L, VideoStatus.FLAGGED);
        service.updatePriority(42L, 5);
        service.updateHidden(42L, true);
        service.deleteVideo(42L, false, false);
        service.enableHls(42L);
        service.disableHls(42L);

        verify(feedCache, times(6)).invalidate();
    }

    @Test
    void failedMutation_doesNotInvalidateTheFeedCache() {
        when(videoSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateHidden(99L, true));

        verify(feedCache, never()).invalidate();
    }
}
