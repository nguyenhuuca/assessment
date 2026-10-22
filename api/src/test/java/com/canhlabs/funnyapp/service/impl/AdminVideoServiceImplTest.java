package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminStatsDto;
import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.CommentStatus;
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

    @InjectMocks AdminVideoServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.injectVideoStorageService(videoStorageService);
        service.injectVideoSourceRepository(videoSourceRepository);
        service.injectUserRepo(userRepo);
        service.injectVideoCommentRepository(commentRepository);
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
}
