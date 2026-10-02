package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.utils.CommentAuthorUtils;
import org.springframework.http.HttpStatus;
import java.util.Objects;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.canhlabs.funnyapp.event.CommentRepliedEvent;
import com.canhlabs.funnyapp.utils.NotificationText;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCommentServiceImpl {
    private static final int MAX_ROOT_HOPS = 50;

    private final VideoCommentRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    /** Reply target: the thread root and the comment actually replied to (before normalization). */
    private record ReplyTarget(String rootId, String parentId) {
    }


    @Transactional(readOnly = true)
    public List<CommentNode> getNestedComments(String videoId) {
        List<VideoComment> all = repo.findAllByVideoIdOrdered(videoId);
        Map<String, CommentNode> map = new LinkedHashMap<>();
        for (VideoComment c : all) {
            map.put(c.getId().toString(), toNode(c));
        }
        List<CommentNode> roots = new ArrayList<>();
        for (VideoComment c : all) {
            if (c.getParentId() == null) {
                roots.add(map.get(c.getId().toString()));
            } else {
                CommentNode parent = map.get(c.getParentId());
                if (parent != null) {
                    if (parent.getReplies() == null) parent.setReplies(new ArrayList<>());
                    parent.getReplies().add(map.get(c.getId().toString()));
                } else {
                    // Orphan handling: treat as root
                    roots.add(map.get(c.getId().toString()));
                }
            }
        }
        List<CommentNode> visibleRoots = new ArrayList<>();
        for (CommentNode root : roots) {
            if (pruneRemoved(root)) visibleRoots.add(root);
        }
        return visibleRoots;
    }

    /**
     * Drops removed leaves; a removed node survives (as placeholder) only while it still has
     * visible descendants. Returns whether the node should be kept.
     */
    private static boolean pruneRemoved(CommentNode node) {
        List<CommentNode> kept = new ArrayList<>();
        for (CommentNode reply : node.getReplies()) {
            if (pruneRemoved(reply)) kept.add(reply);
        }
        node.setReplies(kept);
        return !(node.isRemoved() || node.isDeleted()) || !kept.isEmpty();
    }

    @Transactional
    public CreateCommentResponse createComment(String videoId, CreateCommentRequest req, String guestToken) {
        // Author comes from the JWT only — a client-supplied user id would allow impersonation
        UserDetailDto currentUser = AppUtils.getCurrentUser();
        boolean isGuest = currentUser == null || StringUtils.isBlank(currentUser.getEmail());

        // same guest token must be used for subsequent comments and new comment
        String token = guestToken;

        if (isGuest && guestToken == null) {
            token = UUID.randomUUID().toString();
        }

        ReplyTarget target = resolveReplyTarget(videoId, req.getParentId());

        VideoComment saved = repo.save(VideoComment.builder()
                .videoId(videoId)
                .userId(isGuest ? "" : currentUser.getEmail())
                .guestName(isGuest ? req.getGuestName() : null)
                .guestTokenHash(isGuest ? token : null)
                .content(req.getContent())
                .parentId(target == null ? null : target.rootId())
                .status(CommentStatus.VISIBLE)
                .build());

        if (target != null) {
            // Delivered by NotificationEventListener only after this transaction commits
            eventPublisher.publishEvent(new CommentRepliedEvent(videoId, target.rootId(), target.parentId(),
                    saved.getId(), isGuest ? null : currentUser.getEmail(),
                    NotificationText.snippet(req.getContent())));
        }

        return CreateCommentResponse.builder()
                .id(saved.getId())
                .guestToken(isGuest ? token : null) // return once for guests
                .build();
    }

    /**
     * Validates the reply target and returns the thread root (threads are 2 levels deep) together with
     * the comment replied to. Returns null for a top-level comment.
     */
    private ReplyTarget resolveReplyTarget(String videoId, String parentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        UUID parentUuid;
        try {
            parentUuid = UUID.fromString(parentId.trim());
        } catch (IllegalArgumentException e) {
            throw error(HttpStatus.BAD_REQUEST, 4003, "Invalid parentId");
        }
        VideoComment parent = repo.findById(parentUuid)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, 4042, "Parent comment not found"));
        if (!Objects.equals(videoId, parent.getVideoId())) {
            throw error(HttpStatus.BAD_REQUEST, 4004, "Parent comment belongs to another video");
        }
        if (parent.getStatus() != CommentStatus.VISIBLE) {
            throw error(HttpStatus.BAD_REQUEST, 4005, "Cannot reply to a removed or deleted comment");
        }
        // Walk up to the top-level ancestor; bounded to guard against cycles in legacy data
        VideoComment root = parent;
        for (int hops = 0; hops < MAX_ROOT_HOPS && root.getParentId() != null; hops++) {
            VideoComment next = findById(root.getParentId());
            if (next == null) {
                break;
            }
            root = next;
        }
        return new ReplyTarget(root.getId().toString(), parent.getId().toString());
    }

    private VideoComment findById(String id) {
        try {
            return repo.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static CustomException error(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }

    /**
     * Delete policy:
     * - If authenticated user: may delete own comment or any comment when has ROLE_ADMIN.
     * - If guest: must provide a plain token; compare with stored bcrypt hash.
     */
    @Transactional
    public void deleteComment(UUID commentId, String guestTokenIfAny) {
        VideoComment c = repo.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthed = (auth != null && auth.isAuthenticated());
        boolean isOwnerUser = false;
        log.info("Delete comment request: isAuthed={}, userId={}, commentUserId={}, guestTokenIfAny={}",
                isAuthed, auth != null ? auth.getName() : null, c.getUserId(), guestTokenIfAny != null ? "[PROVIDED]" : "[NOT_PROVIDED]");

        if (isAuthed && c.getUserId() != null) {
            String principalName = auth.getName();
            isOwnerUser = principalName != null && principalName.equals(c.getUserId());
        }

        boolean guestOk = false;
        if (StringUtils.isEmpty(c.getUserId()) && c.getGuestTokenHash() != null && guestTokenIfAny != null) {
            guestOk = guestTokenIfAny.equals(c.getGuestTokenHash());
        }

        if (!(isOwnerUser || guestOk)) {
            throw new SecurityException("Not authorized to delete this comment");
        }

        // Never destroy other people's replies: keep a placeholder when replies exist
        if (repo.findByParentId(c.getId().toString()).isEmpty()) {
            repo.deleteById(c.getId());
        } else {
            c.setStatus(CommentStatus.DELETED);
            c.setContent(""); // column is NOT NULL; author text is erased
            repo.save(c);
        }
    }

    private static CommentNode toNode(VideoComment c) {
        if (c.getStatus() == CommentStatus.REMOVED || c.getStatus() == CommentStatus.DELETED) {
            // Placeholder: never expose removed/deleted content or author
            return CommentNode.builder()
                    .id(c.getId())
                    .videoId(c.getVideoId())
                    .createdAt(c.getCreatedAt())
                    .parentId(c.getParentId())
                    .replies(new ArrayList<>())
                    .removed(c.getStatus() == CommentStatus.REMOVED)
                    .deleted(c.getStatus() == CommentStatus.DELETED)
                    .build();
        }
        // Anonymous alias only for guest comments; authenticated comments are identified by userId
        String guestName = null;
        if (StringUtils.isBlank(c.getUserId())) {
            guestName = CommentAuthorUtils.guestAlias(c.getGuestTokenHash());
        }
        return CommentNode.builder()
                .id(c.getId())
                .videoId(c.getVideoId())
                .userId(c.getUserId())
                .guestName(guestName)
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .parentId(c.getParentId())
                .replies(new ArrayList<>())
                .build();
    }
}
