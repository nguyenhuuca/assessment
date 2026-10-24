package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HlsPlaybackServiceImplTest {

    @TempDir
    Path temp;

    @Mock VideoSourceRepository repo;
    @Mock FeatureFlagService flags;

    private HlsPlaybackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HlsPlaybackServiceImpl(repo, new HlsStorage(temp.resolve("hls")), flags);
    }

    private void file(String fileId, String name) throws IOException {
        Path f = temp.resolve("hls").resolve(fileId).resolve(name);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "x");
    }

    private void video(HlsStatus hls, VideoStatus status, boolean hide) {
        when(repo.findBySourceId("v1")).thenReturn(Optional.of(
                VideoSource.builder().sourceId("v1").hlsStatus(hls).status(status).isHide(hide).build()));
    }

    @Test
    void findPlaylist_readyPublishedVisibleWithFile_isServed() throws IOException {
        file("v1", "playlist.m3u8");
        video(HlsStatus.READY, VideoStatus.PUBLISHED, false);

        assertThat(service.findPlaylist("v1")).isPresent();
    }

    @Test
    void findPlaylist_notServable_isEmpty() throws IOException {
        file("v1", "playlist.m3u8");
        for (HlsStatus s : new HlsStatus[]{HlsStatus.NONE, HlsStatus.PENDING, HlsStatus.PROCESSING, HlsStatus.ERROR}) {
            video(s, VideoStatus.PUBLISHED, false);
            assertThat(service.findPlaylist("v1")).as(s.name()).isEmpty();
        }
        video(HlsStatus.READY, VideoStatus.PUBLISHED, true);
        assertThat(service.findPlaylist("v1")).as("hidden").isEmpty();
        video(HlsStatus.READY, VideoStatus.PENDING, false);
        assertThat(service.findPlaylist("v1")).as("not published").isEmpty();
    }

    @Test
    void findPlaylist_unknownVideoOrMissingFile_isEmpty() {
        assertThat(service.findPlaylist("nope")).isEmpty();
        video(HlsStatus.READY, VideoStatus.PUBLISHED, false);
        assertThat(service.findPlaylist("v1")).isEmpty();
    }

    @Test
    void findSegment_existingFile_noDbLookup() throws IOException {
        file("v1", "seg_0.ts");

        assertThat(service.findSegment("v1", "seg_0.ts")).isPresent();
        assertThat(service.findSegment("v1", "seg_1.ts")).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void findSegment_badInput_throws() {
        assertThatThrownBy(() -> service.findSegment("v1", "../x.ts")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findSegment("..", "seg_0.ts")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hlsEnabled_readsFlagWithFalseDefault() {
        when(flags.isEnabled(AppConstant.Flags.HLS_STREAMING, false)).thenReturn(true);

        assertThat(service.hlsEnabled()).isTrue();
    }
}
