package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoCommentServiceImplTest {

    @Mock
    private VideoCommentRepository repo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private VideoCommentServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private VideoComment buildComment(UUID id, String videoId, String parentId,
                                      String userId, String guestTokenHash, String content) {
        return VideoComment.builder()
                .id(id)
                .videoId(videoId)
                .parentId(parentId)
                .userId(userId)
                .guestTokenHash(guestTokenHash)
                .content(content)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // getNestedComments
    // -------------------------------------------------------------------------

    @Test
    void getNestedComments_emptyList_returnsEmpty() {
        when(repo.findAllByVideoIdOrdered("vid1")).thenReturn(Collections.emptyList());

        List<CommentNode> result = service.getNestedComments("vid1");

        assertThat(result).isEmpty();
    }

    @Test
    void getNestedComments_flatList_allRoots_returnsCorrectCount() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        VideoComment c1 = buildComment(id1, "vid1", null, "user1", null, "comment 1");
        VideoComment c2 = buildComment(id2, "vid1", null, "user2", null, "comment 2");
        VideoComment c3 = buildComment(id3, "vid1", null, "", "tok-hash", "comment 3");

        when(repo.findAllByVideoIdOrdered("vid1")).thenReturn(List.of(c1, c2, c3));

        List<CommentNode> result = service.getNestedComments("vid1");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(1).getId()).isEqualTo(id2);
        assertThat(result.get(2).getId()).isEqualTo(id3);
        // All roots have no replies
        result.forEach(n -> assertThat(n.getReplies()).isEmpty());
    }

    @Test
    void getNestedComments_nestedComment_parentHasReply() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        VideoComment parent = buildComment(parentId, "vid1", null, "user1", null, "parent comment");
        VideoComment child = buildComment(childId, "vid1", parentId.toString(), "user2", null, "reply comment");

        when(repo.findAllByVideoIdOrdered("vid1")).thenReturn(List.of(parent, child));

        List<CommentNode> result = service.getNestedComments("vid1");

        // Only parent should be a root
        assertThat(result).hasSize(1);
        CommentNode parentNode = result.get(0);
        assertThat(parentNode.getId()).isEqualTo(parentId);
        assertThat(parentNode.getReplies()).hasSize(1);
        assertThat(parentNode.getReplies().get(0).getId()).isEqualTo(childId);
    }

    @Test
    void getNestedComments_orphanReply_treatedAsRoot() {
        UUID orphanId = UUID.randomUUID();
        // parentId points to a non-existent comment
        String nonExistentParentId = UUID.randomUUID().toString();

        VideoComment orphan = buildComment(orphanId, "vid1", nonExistentParentId, "user1", null, "orphan comment");

        when(repo.findAllByVideoIdOrdered("vid1")).thenReturn(List.of(orphan));

        List<CommentNode> result = service.getNestedComments("vid1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(orphanId);
    }

    // -------------------------------------------------------------------------
    // createComment
    // -------------------------------------------------------------------------

    @Test
    void createComment_guest_noUserId_noGuestToken_generatesNewToken() {
        UUID savedId = UUID.randomUUID();
        VideoComment saved = buildComment(savedId, "vid1", null, "", "generated-token", "hello");

        when(repo.save(any(VideoComment.class))).thenReturn(saved);

        CreateCommentRequest req = CreateCommentRequest.builder()
                .userId(null)
                .guestName("Alice")
                .content("hello")
                .build();

        CreateCommentResponse response = service.createComment("vid1", req, null);

        assertThat(response.getId()).isEqualTo(savedId);
        // A token must have been generated and returned
        assertThat(response.getGuestToken()).isNotNull().isNotBlank();

        ArgumentCaptor<VideoComment> captor = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(captor.capture());
        VideoComment persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEmpty();
        assertThat(persisted.getGuestName()).isEqualTo("Alice");
        assertThat(persisted.getGuestTokenHash()).isNotNull().isNotBlank();
    }

    @Test
    void createComment_guest_withExistingGuestToken_reusesToken() {
        String existingToken = "existing-token-abc";
        UUID savedId = UUID.randomUUID();
        VideoComment saved = buildComment(savedId, "vid1", null, "", existingToken, "hello again");

        when(repo.save(any(VideoComment.class))).thenReturn(saved);

        CreateCommentRequest req = CreateCommentRequest.builder()
                .userId(null)
                .guestName("Bob")
                .content("hello again")
                .build();

        CreateCommentResponse response = service.createComment("vid1", req, existingToken);

        assertThat(response.getId()).isEqualTo(savedId);
        assertThat(response.getGuestToken()).isEqualTo(existingToken);

        ArgumentCaptor<VideoComment> captor = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getGuestTokenHash()).isEqualTo(existingToken);
    }

    @Test
    void createComment_authenticatedUser_savesUserIdAndNullGuestToken() {
        UUID savedId = UUID.randomUUID();
        VideoComment saved = buildComment(savedId, "vid1", null, "user-42", null, "user comment");

        when(repo.save(any(VideoComment.class))).thenReturn(saved);

        CreateCommentRequest req = CreateCommentRequest.builder()
                .userId("user-42")
                .content("user comment")
                .build();

        CreateCommentResponse response = service.createComment("vid1", req, null);

        assertThat(response.getId()).isEqualTo(savedId);
        // Authenticated users do not receive a guest token
        assertThat(response.getGuestToken()).isNull();

        ArgumentCaptor<VideoComment> captor = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(captor.capture());
        VideoComment persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo("user-42");
        assertThat(persisted.getGuestName()).isNull();
        assertThat(persisted.getGuestTokenHash()).isNull();
    }

    // -------------------------------------------------------------------------
    // deleteComment
    // -------------------------------------------------------------------------

    @Test
    void deleteComment_commentNotFound_throwsNoSuchElementException() {
        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(missingId, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Comment not found");

        verify(repo, never()).deleteById(any());
    }

    @Test
    void deleteComment_authenticatedOwner_deletesCommentAndChildren() {
        UUID commentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        VideoComment comment = buildComment(commentId, "vid1", null, "user-42", null, "owner comment");
        VideoComment child = buildComment(childId, "vid1", commentId.toString(), "user-42", null, "child");

        when(repo.findById(commentId)).thenReturn(Optional.of(comment));
        // First call: children of commentId; second call: children of childId (none)
        when(repo.findByParentId(commentId.toString())).thenReturn(List.of(child));
        when(repo.findByParentId(childId.toString())).thenReturn(Collections.emptyList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user-42", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.deleteComment(commentId, null);

        verify(repo).deleteById(childId);
        verify(repo).deleteById(commentId);
    }

    @Test
    void deleteComment_differentAuthenticatedUser_throwsSecurityException() {
        UUID commentId = UUID.randomUUID();
        VideoComment comment = buildComment(commentId, "vid1", null, "user-42", null, "someone else comment");

        when(repo.findById(commentId)).thenReturn(Optional.of(comment));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user-99", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> service.deleteComment(commentId, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized to delete this comment");

        verify(repo, never()).deleteById(any());
    }

    @Test
    void deleteComment_guestWithCorrectToken_deletesComment() {
        UUID commentId = UUID.randomUUID();
        String plainToken = "plain-guest-token";

        VideoComment comment = buildComment(commentId, "vid1", null, "", plainToken, "guest comment");

        when(repo.findById(commentId)).thenReturn(Optional.of(comment));
        when(repo.findByParentId(commentId.toString())).thenReturn(Collections.emptyList());

        // No authentication set — guest path
        service.deleteComment(commentId, plainToken);

        verify(repo).deleteById(commentId);
    }

    @Test
    void deleteComment_guestWithWrongToken_throwsSecurityException() {
        UUID commentId = UUID.randomUUID();
        String storedToken = "correct-token";
        String wrongToken = "wrong-token";

        VideoComment comment = buildComment(commentId, "vid1", null, "", storedToken, "guest comment");

        when(repo.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.deleteComment(commentId, wrongToken))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized to delete this comment");

        verify(repo, never()).deleteById(any());
    }

    @Test
    void deleteComment_authenticatedOwner_recursivelyDeletesDeepChildren() {
        UUID rootId = UUID.randomUUID();
        UUID child1Id = UUID.randomUUID();
        UUID grandChildId = UUID.randomUUID();

        VideoComment root = buildComment(rootId, "vid1", null, "user-1", null, "root");
        VideoComment child1 = buildComment(child1Id, "vid1", rootId.toString(), "user-1", null, "child");
        VideoComment grandChild = buildComment(grandChildId, "vid1", child1Id.toString(), "user-1", null, "grandchild");

        when(repo.findById(rootId)).thenReturn(Optional.of(root));
        when(repo.findByParentId(rootId.toString())).thenReturn(List.of(child1));
        when(repo.findByParentId(child1Id.toString())).thenReturn(List.of(grandChild));
        when(repo.findByParentId(grandChildId.toString())).thenReturn(Collections.emptyList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user-1", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.deleteComment(rootId, null);

        // Deepest child deleted first, then child, then root
        verify(repo).deleteById(grandChildId);
        verify(repo).deleteById(child1Id);
        verify(repo).deleteById(rootId);
        verify(repo, times(3)).deleteById(any(UUID.class));
    }
}
