package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ReactionType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.VideoReactionRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoReactionService;
import com.canhlabs.funnyapp.utils.AppUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoReactionServiceImpl implements VideoReactionService {

    private final VideoReactionRepository reactionRepository;
    private final VideoSourceRepository videoSourceRepository;

    @Override
    @Transactional(readOnly = true)
    public ReactionSummaryDto getSummary(Long videoId) {
        requireVideo(videoId);
        UserDetailDto user = AppUtils.getCurrentUser();
        return buildSummary(videoId, user == null ? null : user.getId());
    }

    @Override
    @Transactional
    public ReactionSummaryDto react(Long videoId, ReactionType type) {
        UserDetailDto user = requireUser();
        requireVideo(videoId);
        reactionRepository.upsert(user.getId(), videoId, type.name());
        return buildSummary(videoId, user.getId());
    }

    @Override
    @Transactional
    public ReactionSummaryDto removeReaction(Long videoId) {
        UserDetailDto user = requireUser();
        requireVideo(videoId);
        reactionRepository.deleteByIdUserIdAndIdVideoId(user.getId(), videoId);
        return buildSummary(videoId, user.getId());
    }

    private UserDetailDto requireUser() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw CustomException.builder()
                    .status(HttpStatus.UNAUTHORIZED)
                    .subCode(401)
                    .message("Authentication required")
                    .build();
        }
        return user;
    }

    private void requireVideo(Long videoId) {
        if (videoId == null || !videoSourceRepository.existsByIdAndIsHide(videoId, false)) {
            throw CustomException.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .subCode(404)
                    .message("Video not found")
                    .build();
        }
    }

    private ReactionSummaryDto buildSummary(Long videoId, Long userId) {
        long likes = 0;
        long dislikes = 0;
        for (VideoReactionRepository.ReactionCount row : reactionRepository.countByReaction(videoId)) {
            if (row.getReaction() == ReactionType.LIKE) {
                likes = row.getTotal();
            } else if (row.getReaction() == ReactionType.DISLIKE) {
                dislikes = row.getTotal();
            }
        }
        ReactionType mine = userId == null ? null : reactionRepository.findReaction(userId, videoId).orElse(null);
        return ReactionSummaryDto.builder()
                .videoId(videoId)
                .likeCount(likes)
                .dislikeCount(dislikes)
                .myReaction(mine)
                .build();
    }
}
