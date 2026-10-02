package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.CommentModerationReason;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.AdminCommentService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.CommentAuthorUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCommentServiceImpl implements AdminCommentService {

    private static final char LIKE_ESCAPE = '\\';

    private final VideoCommentRepository commentRepository;
    private final VideoSourceRepository videoSourceRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCommentDto> getComments(Pageable pageable, CommentStatus status, String q, String videoId) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<VideoComment> page = commentRepository.findAll(buildSpec(status, q, videoId), effective);
        Map<String, String> titles = loadTitles(page.getContent());
        return page.map(c -> toDto(c, titles.get(c.getVideoId())));
    }

    @Override
    @Transactional
    public AdminCommentDto moderate(UUID id, ModerateCommentRequest request) {
        VideoComment comment = commentRepository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4041, "Comment not found"));

        switch (request.getAction()) {
            case REMOVE -> remove(comment, request);
            case RESTORE -> restore(comment);
        }
        return toDto(comment, loadTitles(List.of(comment)).get(comment.getVideoId()));
    }

    private void remove(VideoComment comment, ModerateCommentRequest request) {
        CommentModerationReason reason = request.getReason();
        if (reason == null) {
            throw error(HttpStatus.BAD_REQUEST, 4001, "Reason is required to remove a comment");
        }
        String note = request.getNote() == null ? null : request.getNote().trim();
        boolean noteBlank = note == null || note.isEmpty();
        if (reason == CommentModerationReason.OTHER && noteBlank) {
            throw error(HttpStatus.BAD_REQUEST, 4002, "Note is required when reason is OTHER");
        }
        if (comment.getStatus() == CommentStatus.REMOVED) {
            return; // idempotent: keep the original moderation record
        }
        UserDetailDto admin = AppUtils.getCurrentUser();
        comment.setStatus(CommentStatus.REMOVED);
        comment.setModerationReason(reason);
        comment.setModerationNote(noteBlank ? null : note);
        comment.setModeratedBy(admin != null ? admin.getEmail() : "unknown");
        comment.setModeratedAt(Instant.now());
        commentRepository.save(comment);
    }

    private void restore(VideoComment comment) {
        if (comment.getStatus() == CommentStatus.VISIBLE) {
            return; // idempotent
        }
        comment.setStatus(CommentStatus.VISIBLE); // moderation_* kept for history
        commentRepository.save(comment);
    }

    private static Specification<VideoComment> buildSpec(CommentStatus status, String q, String videoId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("content")), pattern, LIKE_ESCAPE));
            }
            if (videoId != null && !videoId.isBlank()) {
                predicates.add(cb.equal(root.get("videoId"), videoId.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<String, String> loadTitles(List<VideoComment> comments) {
        Set<Long> ids = new HashSet<>();
        for (VideoComment c : comments) {
            try {
                ids.add(Long.valueOf(c.getVideoId()));
            } catch (NumberFormatException ignored) {
                // non-numeric video id has no title
            }
        }
        Map<String, String> titles = new HashMap<>();
        if (ids.isEmpty()) {
            return titles;
        }
        for (VideoSource v : videoSourceRepository.findAllById(ids)) {
            titles.put(String.valueOf(v.getId()), v.getTitle());
        }
        return titles;
    }

    private static AdminCommentDto toDto(VideoComment c, String title) {
        boolean guest = c.getUserId() == null || c.getUserId().isBlank();
        return AdminCommentDto.builder()
                .id(c.getId())
                .videoId(c.getVideoId())
                .videoTitle(title)
                .authorEmail(guest ? null : c.getUserId())
                .guestName(guest ? CommentAuthorUtils.guestAlias(c.getGuestTokenHash()) : null)
                .isGuest(guest)
                .content(c.getContent())
                .parentId(c.getParentId())
                .createdAt(c.getCreatedAt())
                .status(c.getStatus())
                .moderationReason(c.getModerationReason())
                .moderationNote(c.getModerationNote())
                .moderatedBy(c.getModeratedBy())
                .moderatedAt(c.getModeratedAt())
                .build();
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }
}
