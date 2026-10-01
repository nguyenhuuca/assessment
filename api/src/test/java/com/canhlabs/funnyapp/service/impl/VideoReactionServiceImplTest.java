package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.ReactionType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.VideoReactionRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoReactionServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long VIDEO_ID = 42L;

    @Mock
    private VideoReactionRepository reactionRepository;

    @Mock
    private VideoSourceRepository videoSourceRepository;

    @InjectMocks
    private VideoReactionServiceImpl service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        UserDetailDto user = UserDetailDto.builder().id(USER_ID).email("u@x.com").build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u@x.com", null, List.of());
        auth.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static VideoReactionRepository.ReactionCount row(ReactionType type, long total) {
        return new VideoReactionRepository.ReactionCount() {
            @Override
            public ReactionType getReaction() {
                return type;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private void videoExists() {
        when(videoSourceRepository.existsByIdAndIsHide(VIDEO_ID, false)).thenReturn(true);
    }

    @Test
    void react_like_upsertsAndReturnsSummary() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.LIKE, 10), row(ReactionType.DISLIKE, 2)));
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.of(ReactionType.LIKE));

        ReactionSummaryDto dto = service.react(VIDEO_ID, ReactionType.LIKE);

        verify(reactionRepository).upsert(USER_ID, VIDEO_ID, "LIKE");
        assertThat(dto.getVideoId()).isEqualTo(VIDEO_ID);
        assertThat(dto.getLikeCount()).isEqualTo(10);
        assertThat(dto.getDislikeCount()).isEqualTo(2);
        assertThat(dto.getMyReaction()).isEqualTo(ReactionType.LIKE);
    }

    @Test
    void react_dislike_upsertsDislike() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.DISLIKE, 1)));
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.of(ReactionType.DISLIKE));

        ReactionSummaryDto dto = service.react(VIDEO_ID, ReactionType.DISLIKE);

        verify(reactionRepository).upsert(USER_ID, VIDEO_ID, "DISLIKE");
        assertThat(dto.getLikeCount()).isZero();
        assertThat(dto.getDislikeCount()).isEqualTo(1);
        assertThat(dto.getMyReaction()).isEqualTo(ReactionType.DISLIKE);
    }

    @Test
    void react_switchLikeToDislike_usesSingleUpsert() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.DISLIKE, 3)));
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.of(ReactionType.DISLIKE));

        service.react(VIDEO_ID, ReactionType.DISLIKE);

        verify(reactionRepository).upsert(USER_ID, VIDEO_ID, "DISLIKE");
        verify(reactionRepository, never()).deleteByIdUserIdAndIdVideoId(anyLong(), anyLong());
    }

    @Test
    void removeReaction_deletesAndReturnsSummaryWithNullMine() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.LIKE, 4)));
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.empty());

        ReactionSummaryDto dto = service.removeReaction(VIDEO_ID);

        verify(reactionRepository).deleteByIdUserIdAndIdVideoId(USER_ID, VIDEO_ID);
        assertThat(dto.getLikeCount()).isEqualTo(4);
        assertThat(dto.getMyReaction()).isNull();
    }

    @Test
    void removeReaction_whenNoneExists_isIdempotent() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of());
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.empty());

        ReactionSummaryDto dto = service.removeReaction(VIDEO_ID);

        assertThat(dto.getLikeCount()).isZero();
        assertThat(dto.getDislikeCount()).isZero();
        assertThat(dto.getMyReaction()).isNull();
    }

    @Test
    void react_unknownOrHiddenVideo_throwsNotFound() {
        login();
        when(videoSourceRepository.existsByIdAndIsHide(VIDEO_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.react(VIDEO_ID, ReactionType.LIKE))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(reactionRepository, never()).upsert(anyLong(), anyLong(), anyString());
    }

    @Test
    void removeReaction_unknownVideo_throwsNotFound() {
        login();
        when(videoSourceRepository.existsByIdAndIsHide(VIDEO_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.removeReaction(VIDEO_ID))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(reactionRepository, never()).deleteByIdUserIdAndIdVideoId(anyLong(), anyLong());
    }

    @Test
    void getSummary_unknownVideo_throwsNotFound() {
        when(videoSourceRepository.existsByIdAndIsHide(VIDEO_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.getSummary(VIDEO_ID))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void react_nullVideoId_throwsNotFound() {
        login();

        assertThatThrownBy(() -> service.react(null, ReactionType.LIKE))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void react_unauthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> service.react(VIDEO_ID, ReactionType.LIKE))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(reactionRepository, never()).upsert(anyLong(), anyLong(), anyString());
    }

    @Test
    void removeReaction_unauthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> service.removeReaction(VIDEO_ID))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(reactionRepository, never()).deleteByIdUserIdAndIdVideoId(anyLong(), anyLong());
    }

    @Test
    void getSummary_guest_hasNullMyReactionAndSkipsLookup() {
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.LIKE, 5), row(ReactionType.DISLIKE, 1)));

        ReactionSummaryDto dto = service.getSummary(VIDEO_ID);

        assertThat(dto.getLikeCount()).isEqualTo(5);
        assertThat(dto.getDislikeCount()).isEqualTo(1);
        assertThat(dto.getMyReaction()).isNull();
        verify(reactionRepository, never()).findReaction(any(), any());
    }

    @Test
    void getSummary_authenticated_includesMyReaction() {
        login();
        videoExists();
        when(reactionRepository.countByReaction(VIDEO_ID)).thenReturn(List.of(row(ReactionType.DISLIKE, 1)));
        when(reactionRepository.findReaction(USER_ID, VIDEO_ID)).thenReturn(Optional.of(ReactionType.DISLIKE));

        assertThat(service.getSummary(VIDEO_ID).getMyReaction()).isEqualTo(ReactionType.DISLIKE);
    }
}
