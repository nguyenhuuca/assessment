package com.canhlabs.assessment.service;

import com.canhlabs.assessment.share.dto.ShareRequestDto;
import com.canhlabs.assessment.share.dto.VideoDto;

import java.util.List;

public interface ShareService {
    /**
     * Using to get video info from YouTube and save it to db
     * Using developer api to get video info
     * @param shareRequestDto hold url link
     * @return video info
     */
    VideoDto shareLink(ShareRequestDto shareRequestDto);

    /**
     * Get all link share
     * @return List video info
     */
    List<VideoDto> getALLShare();

    /**
     * using kafka to send message to other system
     */
    void sendInfo(String message);


    /**
     * Using to delete video that shared by user.
     * User only delete video that user created before, not video of other user.
     * @param id of videos
     */
    void deleteVideo(Long id);
}
