package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.service.HlsPlaybackService;
import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HlsPlaybackServiceImpl implements HlsPlaybackService {

    private final VideoSourceRepository videoSourceRepository;
    private final HlsStorage hlsStorage;
    private final FeatureFlagService featureFlagService;

    @Override
    public Optional<Path> findPlaylist(String fileId) {
        return videoSourceRepository.findBySourceId(fileId)
                .filter(HlsPlaybackServiceImpl::isServable)
                .map(v -> hlsStorage.resolvePlaylist(fileId))
                .filter(Files::isRegularFile);
    }

    @Override
    public Optional<Path> findSegment(String fileId, String segment) {
        return Optional.of(hlsStorage.resolveSegment(fileId, segment)).filter(Files::isRegularFile);
    }

    @Override
    public boolean hlsEnabled() {
        return featureFlagService.isEnabled(AppConstant.Flags.HLS_STREAMING, false);
    }

    private static boolean isServable(VideoSource v) {
        return v.getHlsStatus() == HlsStatus.READY && v.getStatus() == VideoStatus.PUBLISHED && v.getVisibility() != VideoVisibility.HIDDEN;
    }
}
