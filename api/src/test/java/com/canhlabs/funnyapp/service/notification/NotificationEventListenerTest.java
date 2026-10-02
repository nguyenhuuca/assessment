package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoComment;
import com.canhlabs.funnyapp.enums.CommentModerationReason;
import com.canhlabs.funnyapp.enums.UserStatus;
import com.canhlabs.funnyapp.event.CommentRemovedEvent;
import com.canhlabs.funnyapp.event.CommentRepliedEvent;
import com.canhlabs.funnyapp.repo.NotificationRepository;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock VideoCommentRepository commentRepository;
    @Mock UserRepo userRepo;
    @Mock NotificationPublisher publisher;

    NotificationEventListener listener;

    final UUID rootId = UUID.randomUUID();
    final UUID parentId = UUID.randomUUID();
    final UUID newId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationRepository, commentRepository, userRepo,
                publisher, new ObjectMapper());
    }

    private static VideoComment comment(UUID id, String author) {
        return VideoComment.builder().id(id).videoId("22").userId(author).content("x").build();
    }

    private static User user(long id, String email, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setUserName(email);
        u.setStatus(status);
        return u;
    }

    private CommentRepliedEvent reply(String actor) {
        return new CommentRepliedEvent("22", rootId.toString(), parentId.toString(), newId, actor, "haha same");
    }

    private void stubAuthors(String rootAuthor, String parentAuthor) {
        when(commentRepository.findById(rootId)).thenReturn(Optional.of(comment(rootId, rootAuthor)));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(comment(parentId, parentAuthor)));
    }

    // ── reply recipients ──────────────────────────────────────────────────────

    @Test
    void reply_notifiesRootAndParentAuthorsWithSharedGroupKeyPerThread() {
        stubAuthors("root@x.com", "parent@x.com");
        when(userRepo.findAllByUserName("root@x.com")).thenReturn(user(1L, "root@x.com", UserStatus.ACTIVE));
        when(userRepo.findAllByUserName("parent@x.com")).thenReturn(user(2L, "parent@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply("bob@x.com"));

        String key = "REPLY:" + rootId;
        verify(notificationRepository).upsertGrouped(eq(1L), eq("COMMENT_REPLY"), eq(key), eq("22"),
                eq(newId.toString()), eq("bob"), eq("{\"snippet\":\"haha same\"}"));
        verify(notificationRepository).upsertGrouped(eq(2L), eq("COMMENT_REPLY"), eq(key), eq("22"),
                eq(newId.toString()), eq("bob"), any());
        verify(publisher).unreadChanged(1L);
        verify(publisher).unreadChanged(2L);
    }

    @Test
    void reply_samePersonRootAndParent_getsOneRow() {
        stubAuthors("Root@x.com", "root@x.com");
        when(userRepo.findAllByUserName("Root@x.com")).thenReturn(user(1L, "Root@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply("bob@x.com"));

        verify(notificationRepository).upsertGrouped(eq(1L), any(), any(), any(), any(), any(), any());
        verify(publisher).unreadChanged(1L);
    }

    @Test
    void reply_actorExcluded() {
        stubAuthors("bob@x.com", "parent@x.com");
        when(userRepo.findAllByUserName("parent@x.com")).thenReturn(user(2L, "parent@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply("BOB@x.com"));

        verify(notificationRepository).upsertGrouped(eq(2L), any(), any(), any(), any(), any(), any());
        verify(publisher, never()).unreadChanged(1L);
        verify(userRepo, never()).findAllByUserName("bob@x.com");
    }

    @Test
    void reply_replyToOwnThreadByActor_noNotifications() {
        stubAuthors("bob@x.com", "bob@x.com");

        listener.onCommentReplied(reply("bob@x.com"));

        verifyNoInteractions(notificationRepository, publisher, userRepo);
    }

    @Test
    void reply_guestAuthorsSkipped() {
        stubAuthors("", "parent@x.com");
        when(userRepo.findAllByUserName("parent@x.com")).thenReturn(user(2L, "parent@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply("bob@x.com"));

        verify(notificationRepository).upsertGrouped(eq(2L), any(), any(), any(), any(), any(), any());
        verify(notificationRepository, never()).upsertGrouped(eq(1L), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reply_unknownAndDeactivatedUsersSkipped() {
        stubAuthors("ghost@x.com", "gone@x.com");
        when(userRepo.findAllByUserName("ghost@x.com")).thenReturn(null);
        when(userRepo.findAllByUserName("gone@x.com")).thenReturn(user(3L, "gone@x.com", UserStatus.DEACTIVATED));

        listener.onCommentReplied(reply("bob@x.com"));

        verifyNoInteractions(notificationRepository, publisher);
    }

    @Test
    void reply_guestActor_usesGuestDisplay() {
        stubAuthors("root@x.com", "root@x.com");
        when(userRepo.findAllByUserName("root@x.com")).thenReturn(user(1L, "root@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply(null));

        verify(notificationRepository).upsertGrouped(eq(1L), any(), any(), any(), any(), eq("guest"), any());
    }

    @Test
    void reply_missingParentComment_stillNotifiesRoot() {
        when(commentRepository.findById(rootId)).thenReturn(Optional.of(comment(rootId, "root@x.com")));
        when(commentRepository.findById(parentId)).thenReturn(Optional.empty());
        when(userRepo.findAllByUserName("root@x.com")).thenReturn(user(1L, "root@x.com", UserStatus.ACTIVE));

        listener.onCommentReplied(reply("bob@x.com"));

        verify(notificationRepository).upsertGrouped(eq(1L), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reply_invalidIds_areIgnored() {
        CommentRepliedEvent bad = new CommentRepliedEvent("22", "not-a-uuid", null, newId, "bob@x.com", "s");

        assertThatCode(() -> listener.onCommentReplied(bad)).doesNotThrowAnyException();

        verifyNoInteractions(notificationRepository, publisher);
    }

    // ── error isolation ───────────────────────────────────────────────────────

    @Test
    void reply_oneRecipientFails_otherStillNotified_noExceptionEscapes() {
        stubAuthors("root@x.com", "parent@x.com");
        when(userRepo.findAllByUserName("root@x.com")).thenReturn(user(1L, "root@x.com", UserStatus.ACTIVE));
        when(userRepo.findAllByUserName("parent@x.com")).thenReturn(user(2L, "parent@x.com", UserStatus.ACTIVE));
        when(notificationRepository.upsertGrouped(eq(1L), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onCommentReplied(reply("bob@x.com"))).doesNotThrowAnyException();

        verify(notificationRepository).upsertGrouped(eq(2L), any(), any(), any(), any(), any(), any());
        verify(publisher).unreadChanged(2L);
        verify(publisher, never()).unreadChanged(1L);
    }

    @Test
    void reply_commentLookupThrows_isSwallowed() {
        when(commentRepository.findById(any(UUID.class))).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onCommentReplied(reply("bob@x.com"))).doesNotThrowAnyException();

        verifyNoInteractions(publisher);
    }

    @Test
    void reply_publisherFails_isSwallowed() {
        stubAuthors("root@x.com", "root@x.com");
        when(userRepo.findAllByUserName("root@x.com")).thenReturn(user(1L, "root@x.com", UserStatus.ACTIVE));
        org.mockito.Mockito.doThrow(new RuntimeException("push failed")).when(publisher).unreadChanged(anyLong());

        assertThatCode(() -> listener.onCommentReplied(reply("bob@x.com"))).doesNotThrowAnyException();
    }

    // ── removed ───────────────────────────────────────────────────────────────

    @Test
    void removed_notifiesAuthorWithReasonOnlyAndPerCommentGroupKey() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID guestComment = UUID.randomUUID();
        when(userRepo.findAllByUserName("a@x.com")).thenReturn(user(1L, "a@x.com", UserStatus.ACTIVE));
        when(userRepo.findAllByUserName("b@x.com")).thenReturn(user(2L, "b@x.com", UserStatus.ACTIVE));
        CommentRemovedEvent event = new CommentRemovedEvent(List.of(
                new CommentRemovedEvent.RemovedComment(c1, "22", "a@x.com"),
                new CommentRemovedEvent.RemovedComment(c2, "23", "b@x.com"),
                new CommentRemovedEvent.RemovedComment(guestComment, "24", "")), CommentModerationReason.SPAM);

        listener.onCommentRemoved(event);

        verify(notificationRepository).upsertGrouped(1L, "COMMENT_REMOVED", "REMOVED:" + c1, "22",
                c1.toString(), null, "{\"reason\":\"SPAM\"}");
        verify(notificationRepository).upsertGrouped(2L, "COMMENT_REMOVED", "REMOVED:" + c2, "23",
                c2.toString(), null, "{\"reason\":\"SPAM\"}");
        verify(publisher).unreadChanged(1L);
        verify(publisher).unreadChanged(2L);
        verify(notificationRepository, org.mockito.Mockito.times(2))
                .upsertGrouped(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void removed_deactivatedAuthorSkipped_failureOfOneDoesNotStopOthers() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        when(userRepo.findAllByUserName("a@x.com")).thenThrow(new IllegalStateException("db down"));
        when(userRepo.findAllByUserName("gone@x.com")).thenReturn(user(5L, "gone@x.com", UserStatus.DEACTIVATED));
        when(userRepo.findAllByUserName("c@x.com")).thenReturn(user(3L, "c@x.com", UserStatus.ACTIVE));
        CommentRemovedEvent event = new CommentRemovedEvent(List.of(
                new CommentRemovedEvent.RemovedComment(c1, "22", "a@x.com"),
                new CommentRemovedEvent.RemovedComment(c2, "22", "gone@x.com"),
                new CommentRemovedEvent.RemovedComment(c3, "22", "c@x.com")), CommentModerationReason.OTHER);

        assertThatCode(() -> listener.onCommentRemoved(event)).doesNotThrowAnyException();

        verify(notificationRepository).upsertGrouped(eq(3L), any(), any(), any(), any(), any(), any());
        verify(publisher).unreadChanged(3L);
        verify(publisher, never()).unreadChanged(5L);
    }

    @Test
    void removed_nullCommentList_isSwallowed() {
        assertThatCode(() -> listener.onCommentRemoved(new CommentRemovedEvent(null, CommentModerationReason.SPAM)))
                .doesNotThrowAnyException();
    }
}
