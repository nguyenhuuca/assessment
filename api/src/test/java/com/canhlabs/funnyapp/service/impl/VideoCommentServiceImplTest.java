package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentRequest;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.CommentStatus;
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

    private void authenticateAs(String email) {
        UserDetailDto user = new UserDetailDto();
        user.setId(42L);
        user.setEmail(email);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
        auth.setDetails(user);
        SecurityContextHolder.getContext().setAuthentication(auth);
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
    void getNestedComments_anonymousAliasOnlyForGuests() {
        VideoComment user = buildComment(UUID.randomUUID(), "vid1", null, "a@b.com", null, "from user");
        VideoComment guest = buildComment(UUID.randomUUID(), "vid1", null, "", "tok-hash", "from guest");
        when(repo.findAllByVideoIdOrdered("vid1")).thenReturn(List.of(user, guest));

        List<CommentNode> result = service.getNestedComments("vid1");

        assertThat(result.get(0).getUserId()).isEqualTo("a@b.com");
        assertThat(result.get(0).getGuestName()).isNull();
        assertThat(result.get(1).getGuestName()).startsWith("Anonymous");
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
        VideoComment saved = buildComment(savedId, "vid1", null, "user-42@mail.com", null, "user comment");

        when(repo.save(any(VideoComment.class))).thenReturn(saved);
        authenticateAs("user-42@mail.com");

        CreateCommentRequest req = CreateCommentRequest.builder()
                .content("user comment")
                .build();

        // A stale guest token from before login must not turn an authenticated user into a guest
        CreateCommentResponse response = service.createComment("vid1", req, "old-guest-token");

        assertThat(response.getId()).isEqualTo(savedId);
        // Authenticated users do not receive a guest token
        assertThat(response.getGuestToken()).isNull();

        ArgumentCaptor<VideoComment> captor = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(captor.capture());
        VideoComment persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo("user-42@mail.com");
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
    void deleteComment_authenticatedOwner_withReplies_softDeletesAndKeepsReplies() {
        UUID commentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        VideoComment comment = buildComment(commentId, "vid1", null, "user-42", null, "owner comment");
        VideoComment child = buildComment(childId, "vid1", commentId.toString(), "other-user", null, "child");

        when(repo.findById(commentId)).thenReturn(Optional.of(comment));
        when(repo.findByParentId(commentId.toString())).thenReturn(List.of(child));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user-42", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.deleteComment(commentId, null);

        verify(repo, never()).deleteById(any());
        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(cap.getValue().getContent()).isEmpty();
    }

    @Test
    void deleteComment_authenticatedOwner_leaf_hardDeletes() {
        UUID commentId = UUID.randomUUID();
        VideoComment comment = buildComment(commentId, "vid1", UUID.randomUUID().toString(), "user-42", null, "mine");
        when(repo.findById(commentId)).thenReturn(Optional.of(comment));
        when(repo.findByParentId(commentId.toString())).thenReturn(Collections.emptyList());
        authenticateAs("user-42");

        service.deleteComment(commentId, null);

        verify(repo).deleteById(commentId);
        verify(repo, never()).save(any());
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
    void deleteComment_authenticatedOwner_deepThread_onlySoftDeletesTargetNeverCascades() {
        UUID rootId = UUID.randomUUID();
        UUID child1Id = UUID.randomUUID();

        VideoComment root = buildComment(rootId, "vid1", null, "user-1", null, "root");
        VideoComment child1 = buildComment(child1Id, "vid1", rootId.toString(), "user-2", null, "child");

        when(repo.findById(rootId)).thenReturn(Optional.of(root));
        when(repo.findByParentId(rootId.toString())).thenReturn(List.of(child1));
        authenticateAs("user-1");

        service.deleteComment(rootId, null);

        verify(repo, never()).deleteById(any());
        verify(repo, times(1)).save(any(VideoComment.class));
        assertThat(child1.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(child1.getContent()).isEqualTo("child");
    }

    // -------------------------------------------------------------------------
    // Deleted placeholders
    // -------------------------------------------------------------------------

    private VideoComment withStatus(VideoComment c, CommentStatus s) {
        c.setStatus(s);
        return c;
    }

    @Test
    void getNestedComments_deletedRootWithVisibleReply_becomesDeletedPlaceholder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                withStatus(buildComment(a, "v", null, "me@x.com", null, ""), CommentStatus.DELETED),
                buildComment(b, "v", a.toString(), "ok@x.com", null, "reply")));

        List<CommentNode> result = service.getNestedComments("v");

        assertThat(result).hasSize(1);
        CommentNode ph = result.get(0);
        assertThat(ph.isDeleted()).isTrue();
        assertThat(ph.isRemoved()).isFalse();
        assertThat(ph.getContent()).isNull();
        assertThat(ph.getUserId()).isNull();
        assertThat(ph.getGuestName()).isNull();
        assertThat(ph.getReplies()).hasSize(1);
        assertThat(ph.getReplies().get(0).isDeleted()).isFalse();
    }

    @Test
    void getNestedComments_deletedLeaf_isOmitted() {
        UUID a = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                withStatus(buildComment(a, "v", null, "me@x.com", null, ""), CommentStatus.DELETED)));

        assertThat(service.getNestedComments("v")).isEmpty();
    }

    @Test
    void getNestedComments_deletedRootWithOnlyRemovedReply_isOmitted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                withStatus(buildComment(a, "v", null, "me@x.com", null, ""), CommentStatus.DELETED),
                removed(buildComment(b, "v", a.toString(), "bad@x.com", null, "x"))));

        assertThat(service.getNestedComments("v")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Reply validation
    // -------------------------------------------------------------------------

    private CreateCommentRequest reply(String parentId) {
        return CreateCommentRequest.builder().content("re").parentId(parentId).build();
    }

    private void stubSave() {
        when(repo.save(any(VideoComment.class))).thenAnswer(inv -> {
            VideoComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    private static void assertHttp(Throwable t, org.springframework.http.HttpStatus status) {
        assertThat(t).isInstanceOfSatisfying(com.canhlabs.funnyapp.exception.CustomException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Test
    void createComment_replyToRoot_storesRootId() {
        authenticateAs("u@x.com");
        UUID rootId = UUID.randomUUID();
        when(repo.findById(rootId)).thenReturn(Optional.of(buildComment(rootId, "v", null, "a@x.com", null, "root")));
        stubSave();

        service.createComment("v", reply(rootId.toString()), null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(rootId.toString());
    }

    @Test
    void createComment_replyToReply_normalizesToRoot() {
        authenticateAs("u@x.com");
        UUID rootId = UUID.randomUUID();
        UUID midId = UUID.randomUUID();
        UUID leafId = UUID.randomUUID();
        when(repo.findById(leafId)).thenReturn(Optional.of(buildComment(leafId, "v", midId.toString(), "c@x.com", null, "leaf")));
        when(repo.findById(midId)).thenReturn(Optional.of(buildComment(midId, "v", rootId.toString(), "b@x.com", null, "mid")));
        when(repo.findById(rootId)).thenReturn(Optional.of(buildComment(rootId, "v", null, "a@x.com", null, "root")));
        stubSave();

        service.createComment("v", reply(leafId.toString()), null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(rootId.toString());
    }

    @Test
    void createComment_replyWithCyclicLegacyData_terminatesAfterBoundedHops() {
        authenticateAs("u@x.com");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findById(a)).thenReturn(Optional.of(buildComment(a, "v", b.toString(), "a@x.com", null, "a")));
        when(repo.findById(b)).thenReturn(Optional.of(buildComment(b, "v", a.toString(), "b@x.com", null, "b")));
        stubSave();

        service.createComment("v", reply(a.toString()), null);

        verify(repo, times(51)).findById(any(UUID.class));
        verify(repo).save(any(VideoComment.class));
    }

    @Test
    void createComment_replyWithDanglingAncestor_usesLastKnownAncestor() {
        authenticateAs("u@x.com");
        UUID a = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(repo.findById(a)).thenReturn(Optional.of(buildComment(a, "v", gone.toString(), "a@x.com", null, "a")));
        when(repo.findById(gone)).thenReturn(Optional.empty());
        stubSave();

        service.createComment("v", reply(a.toString()), null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(a.toString());
    }

    @Test
    void createComment_replyWithNonUuidAncestor_usesLastKnownAncestor() {
        authenticateAs("u@x.com");
        UUID a = UUID.randomUUID();
        when(repo.findById(a)).thenReturn(Optional.of(buildComment(a, "v", "not-a-uuid", "a@x.com", null, "a")));
        stubSave();

        service.createComment("v", reply(a.toString()), null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(a.toString());
    }

    @Test
    void createComment_blankParentId_isTopLevel() {
        authenticateAs("u@x.com");
        stubSave();

        service.createComment("v", reply("  "), null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isNull();
    }

    @Test
    void createComment_invalidParentUuid_returns400() {
        authenticateAs("u@x.com");

        assertThatThrownBy(() -> service.createComment("v", reply("nope"), null))
                .satisfies(t -> assertHttp(t, org.springframework.http.HttpStatus.BAD_REQUEST));
        verify(repo, never()).save(any());
    }

    @Test
    void createComment_parentMissing_returns404() {
        authenticateAs("u@x.com");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createComment("v", reply(id.toString()), null))
                .satisfies(t -> assertHttp(t, org.springframework.http.HttpStatus.NOT_FOUND));
        verify(repo, never()).save(any());
    }

    @Test
    void createComment_parentOnOtherVideo_returns400() {
        authenticateAs("u@x.com");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(buildComment(id, "other", null, "a@x.com", null, "p")));

        assertThatThrownBy(() -> service.createComment("v", reply(id.toString()), null))
                .satisfies(t -> assertHttp(t, org.springframework.http.HttpStatus.BAD_REQUEST));
        verify(repo, never()).save(any());
    }

    @Test
    void createComment_parentRemoved_returns400() {
        authenticateAs("u@x.com");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
                removed(buildComment(id, "v", null, "a@x.com", null, "p"))));

        assertThatThrownBy(() -> service.createComment("v", reply(id.toString()), null))
                .satisfies(t -> assertHttp(t, org.springframework.http.HttpStatus.BAD_REQUEST));
        verify(repo, never()).save(any());
    }

    @Test
    void createComment_parentDeleted_returns400() {
        authenticateAs("u@x.com");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
                withStatus(buildComment(id, "v", null, "a@x.com", null, ""), CommentStatus.DELETED)));

        assertThatThrownBy(() -> service.createComment("v", reply(id.toString()), null))
                .satisfies(t -> assertHttp(t, org.springframework.http.HttpStatus.BAD_REQUEST));
        verify(repo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Moderation placeholders
    // -------------------------------------------------------------------------

    private VideoComment removed(VideoComment c) {
        c.setStatus(CommentStatus.REMOVED);
        return c;
    }

    @Test
    void getNestedComments_removedLeaf_isOmitted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                buildComment(a, "v", null, "u@x.com", null, "keep"),
                removed(buildComment(b, "v", a.toString(), "bad@x.com", null, "secret"))));

        List<CommentNode> result = service.getNestedComments("v");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReplies()).isEmpty();
    }

    @Test
    void getNestedComments_removedRootWithVisibleReply_becomesPlaceholder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                removed(buildComment(a, "v", null, "bad@x.com", null, "secret")),
                buildComment(b, "v", a.toString(), "ok@x.com", null, "reply")));

        List<CommentNode> result = service.getNestedComments("v");

        assertThat(result).hasSize(1);
        CommentNode ph = result.get(0);
        assertThat(ph.isRemoved()).isTrue();
        assertThat(ph.getContent()).isNull();
        assertThat(ph.getUserId()).isNull();
        assertThat(ph.getGuestName()).isNull();
        assertThat(ph.getReplies()).hasSize(1);
        assertThat(ph.getReplies().get(0).getContent()).isEqualTo("reply");
        assertThat(ph.getReplies().get(0).isRemoved()).isFalse();
    }

    @Test
    void getNestedComments_removedGuestComment_doesNotExposeAlias() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                removed(buildComment(a, "v", null, "", "tok", "secret")),
                buildComment(b, "v", a.toString(), "ok@x.com", null, "reply")));

        CommentNode ph = service.getNestedComments("v").get(0);

        assertThat(ph.getGuestName()).isNull();
        assertThat(ph.getContent()).isNull();
    }

    @Test
    void getNestedComments_removedChainWithOnlyRemovedDescendants_isOmitted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                removed(buildComment(a, "v", null, "x@x.com", null, "s1")),
                removed(buildComment(b, "v", a.toString(), "y@x.com", null, "s2"))));

        assertThat(service.getNestedComments("v")).isEmpty();
    }

    @Test
    void getNestedComments_removedMiddleWithVisibleGrandchild_keepsChain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(repo.findAllByVideoIdOrdered("v")).thenReturn(List.of(
                buildComment(a, "v", null, "a@x.com", null, "root"),
                removed(buildComment(b, "v", a.toString(), "b@x.com", null, "gone")),
                buildComment(c, "v", b.toString(), "c@x.com", null, "deep")));

        CommentNode root = service.getNestedComments("v").get(0);

        assertThat(root.getReplies()).hasSize(1);
        CommentNode mid = root.getReplies().get(0);
        assertThat(mid.isRemoved()).isTrue();
        assertThat(mid.getReplies().get(0).getContent()).isEqualTo("deep");
    }

    @Test
    void createComment_newComment_isVisible() {
        authenticateAs("u@x.com");
        when(repo.save(any(VideoComment.class))).thenAnswer(inv -> {
            VideoComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("hi");

        service.createComment("v", req, null);

        ArgumentCaptor<VideoComment> cap = ArgumentCaptor.forClass(VideoComment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }
}
