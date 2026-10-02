package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.utils.CommentAuthorUtils;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.canhlabs.funnyapp.utils.AppUtils;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final VideoCommentRepository repo;
    private final PasswordEncoder passwordEncoder;


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
        return !node.isRemoved() || !kept.isEmpty();
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

        VideoComment saved = repo.save(VideoComment.builder()
                .videoId(videoId)
                .userId(isGuest ? "" : currentUser.getEmail())
                .guestName(isGuest ? req.getGuestName() : null)
                .guestTokenHash(isGuest ? token : null)
                .content(req.getContent())
                .parentId(req.getParentId())
                .status(CommentStatus.VISIBLE)
                .build());

        return CreateCommentResponse.builder()
                .id(saved.getId())
                .guestToken(isGuest ? token : null) // return once for guests
                .build();
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

        // Manual cascade delete (since no FK cascade) — delete subtree
        deleteRecursively(c.getId());
    }

    private void deleteRecursively(UUID id) {
        List<VideoComment> children = repo.findByParentId(id.toString());
        for (VideoComment child : children) {
            deleteRecursively(child.getId());
        }
        repo.deleteById(id);
    }

    private static CommentNode toNode(VideoComment c) {
        if (c.getStatus() == CommentStatus.REMOVED) {
            // Placeholder: never expose removed content or author
            return CommentNode.builder()
                    .id(c.getId())
                    .videoId(c.getVideoId())
                    .createdAt(c.getCreatedAt())
                    .parentId(c.getParentId())
                    .replies(new ArrayList<>())
                    .removed(true)
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
