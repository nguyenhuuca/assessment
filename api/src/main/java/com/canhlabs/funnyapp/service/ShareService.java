package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.ShareRequestDto;
import com.canhlabs.funnyapp.dto.VideoDto;

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

    List<VideoDto> getShareByUser(String userName);

    String deleteShareLink(Long videoId);


}
