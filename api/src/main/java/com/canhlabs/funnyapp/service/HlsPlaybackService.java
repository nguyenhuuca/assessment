package com.canhlabs.funnyapp.service;

import java.nio.file.Path;
import java.util.Optional;

/** Public HLS playback rules (BR-8, BR-13, ADR-0031). Inputs must already be validated by the caller. */
public interface HlsPlaybackService {

    /** Playlist file when the video is READY + PUBLISHED + not hidden and the file exists. */
    Optional<Path> findPlaylist(String fileId);

    /** Segment file when it exists in the rendition directory; no database lookup. */
    Optional<Path> findSegment(String fileId, String segment);

    /** Whether {@code hlsUrl} may be offered; callers read it once per request. */
    boolean hlsEnabled();
}
