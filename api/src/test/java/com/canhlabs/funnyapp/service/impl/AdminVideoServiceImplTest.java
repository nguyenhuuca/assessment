package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.AdminStatsDto;
import com.canhlabs.funnyapp.dto.AdminVideoDto;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
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

    @InjectMocks AdminVideoServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.injectVideoSourceRepository(videoSourceRepository);
        service.injectUserRepo(userRepo);
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
        Pageable pageable = PageRequest.of(0, 20);
        when(videoSourceRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(source)));

        Page<AdminVideoDto> result = service.getVideos(pageable, null);

        assertEquals(1, result.getContent().size());
        AdminVideoDto dto = result.getContent().get(0);
        assertEquals(1L, dto.getId());
        assertEquals("My Video", dto.getTitle());
        assertEquals("https://thumb.jpg", dto.getThumbnailPath());
        assertEquals(VideoStatus.PUBLISHED, dto.getStatus());
        assertEquals(0L, dto.getViewCount());
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

    // ── deleteVideo ───────────────────────────────────────────────────────────

    @Test
    void deleteVideo_callsDeleteById() {
        service.deleteVideo(7L);
        verify(videoSourceRepository).deleteById(7L);
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsAggregatedCounts() {
        when(videoSourceRepository.count()).thenReturn(100L);
        when(videoSourceRepository.countByStatus(VideoStatus.PENDING)).thenReturn(12L);
        when(videoSourceRepository.countByStatus(VideoStatus.FLAGGED)).thenReturn(5L);
        when(userRepo.count()).thenReturn(42L);

        AdminStatsDto stats = service.getStats();

        assertEquals(100L, stats.getTotalVideos());
        assertEquals(42L,  stats.getTotalUsers());
        assertEquals(12L,  stats.getPendingCount());
        assertEquals(5L,   stats.getFlaggedCount());
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
