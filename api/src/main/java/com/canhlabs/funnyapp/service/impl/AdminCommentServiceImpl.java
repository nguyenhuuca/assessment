package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.BulkModerateCommentRequest;
import com.canhlabs.funnyapp.dto.admin.BulkModerationResultDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.CommentModerationAction;
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
import java.util.LinkedHashSet;
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
        String note = validate(request.getAction(), request.getReason(), request.getNote());
        VideoComment comment = commentRepository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4041, "Comment not found"));

        if (apply(comment, request.getAction(), request.getReason(), note, moderator(), Instant.now())) {
            commentRepository.save(comment);
        }
        return toDto(comment, loadTitles(List.of(comment)).get(comment.getVideoId()));
    }

    @Override
    @Transactional
    public BulkModerationResultDto bulkModerate(BulkModerateCommentRequest request) {
        String note = validate(request.getAction(), request.getReason(), request.getNote());
        Set<UUID> ids = new LinkedHashSet<>(request.getIds());
        List<VideoComment> comments = commentRepository.findAllById(ids);

        String moderator = moderator();
        Instant now = Instant.now();
        List<VideoComment> changed = new ArrayList<>();
        Set<UUID> found = new HashSet<>();
        for (VideoComment c : comments) {
            found.add(c.getId());
            if (apply(c, request.getAction(), request.getReason(), note, moderator, now)) {
                changed.add(c);
            }
        }
        commentRepository.saveAll(changed);

        List<UUID> notFound = ids.stream().filter(id -> !found.contains(id)).toList();
        return BulkModerationResultDto.builder()
                .requested(ids.size())
                .updated(changed.size())
                .unchanged(found.size() - changed.size())
                .notFound(notFound)
                .build();
    }

    /**
     * Validates reason/note for the action once, before touching any row. Returns the trimmed note (or null).
     */
    private static String validate(CommentModerationAction action, CommentModerationReason reason, String rawNote) {
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.trim();
        if (action == CommentModerationAction.REMOVE) {
            if (reason == null) {
                throw error(HttpStatus.BAD_REQUEST, 4001, "Reason is required to remove a comment");
            }
            if (reason == CommentModerationReason.OTHER && note == null) {
                throw error(HttpStatus.BAD_REQUEST, 4002, "Note is required when reason is OTHER");
            }
        }
        return note;
    }

    /**
     * Applies the action in memory. Idempotent: returns false when the comment is already in the target state
     * (a second REMOVE keeps the original moderation record).
     */
    private static boolean apply(VideoComment comment, CommentModerationAction action,
                                 CommentModerationReason reason, String note, String moderator, Instant now) {
        if (action == CommentModerationAction.REMOVE) {
            if (comment.getStatus() == CommentStatus.REMOVED) {
                return false;
            }
            comment.setStatus(CommentStatus.REMOVED);
            comment.setModerationReason(reason);
            comment.setModerationNote(note);
            comment.setModeratedBy(moderator);
            comment.setModeratedAt(now);
            return true;
        }
        if (comment.getStatus() == CommentStatus.VISIBLE) {
            return false;
        }
        comment.setStatus(CommentStatus.VISIBLE); // moderation_* kept for history
        return true;
    }

    private static String moderator() {
        UserDetailDto admin = AppUtils.getCurrentUser();
        return admin != null ? admin.getEmail() : "unknown";
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
