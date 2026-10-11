package com.canhlabs.funnyapp.service;

public interface VideoViewService {

    /**
     * Counts one view of the video for this viewer, at most once per dedup window.
     *
     * @param viewerKey "USER:&lt;email&gt;" or "IP:&lt;client ip&gt;"
     * @return true when the view was counted
     */
    boolean recordView(Long videoId, String viewerKey);
}
