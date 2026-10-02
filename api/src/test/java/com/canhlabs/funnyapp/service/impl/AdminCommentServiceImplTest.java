package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
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
import com.canhlabs.funnyapp.utils.AppUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class AdminCommentServiceImplTest {

    @Mock VideoCommentRepository commentRepository;
    @Mock VideoSourceRepository videoSourceRepository;
    @InjectMocks AdminCommentServiceImpl service;

    private static VideoComment comment(String videoId, String userId) {
        return VideoComment.builder()
                .id(UUID.randomUUID()).videoId(videoId).userId(userId)
                .guestTokenHash("tok").content("hello").createdAt(Instant.now()).build();
    }

    private static VideoSource video(long id, String title) {
        VideoSource v = new VideoSource();
        v.setId(id);
        v.setTitle(title);
        return v;
    }

    private static ModerateCommentRequest req(CommentModerationAction a, CommentModerationReason r, String note) {
        return ModerateCommentRequest.builder().action(a).reason(r).note(note).build();
    }

    // ── getComments ───────────────────────────────────────────────────────────

    @Test
    void getComments_mapsTitlesInBatchAndAuthorFields() {
        VideoComment member = comment("14", "a@b.com");
        VideoComment guest = comment("15", "");
        VideoComment oddId = comment("abc", "c@d.com");
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(commentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(member, guest, oddId), pageable, 3));
        when(videoSourceRepository.findAllById(Set.of(14L, 15L)))
                .thenReturn(List.of(video(14L, "Funny cat")));

        Page<AdminCommentDto> result = service.getComments(pageable, null, null, null);

        AdminCommentDto m = result.getContent().get(0);
        assertThat(m.getVideoTitle()).isEqualTo("Funny cat");
        assertThat(m.getAuthorEmail()).isEqualTo("a@b.com");
        assertThat(m.getIsGuest()).isFalse();
        assertThat(m.getGuestName()).isNull();
        AdminCommentDto g = result.getContent().get(1);
        assertThat(g.getVideoTitle()).isNull();
        assertThat(g.getIsGuest()).isTrue();
        assertThat(g.getAuthorEmail()).isNull();
        assertThat(g.getGuestName()).startsWith("Anonymous");
        assertThat(result.getContent().get(2).getVideoTitle()).isNull();
        verify(videoSourceRepository).findAllById(Set.of(14L, 15L));
    }

    @Test
    void getComments_unsortedPageable_defaultsToCreatedAtDesc() {
        when(commentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.getComments(PageRequest.of(1, 10), null, null, null);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findAll(any(Specification.class), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isEqualTo(1);
        assertThat(cap.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void getComments_noNumericVideoIds_skipsTitleLookup() {
        when(commentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment("abc", "a@b.com"))));

        service.getComments(PageRequest.of(0, 5), null, null, null);

        verify(videoSourceRepository, never()).findAllById(any());
    }

    @Test
    void getComments_specificationAppliesAllFilters() {
        when(commentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getComments(PageRequest.of(0, 5), CommentStatus.REMOVED, "50%_Off", " 14 ");
        ArgumentCaptor<Specification> cap = ArgumentCaptor.forClass(Specification.class);
        verify(commentRepository).findAll(cap.capture(), any(Pageable.class));

        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Expression lowered = mock(Expression.class);
        Predicate p = mock(Predicate.class);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(cb.lower(any())).thenReturn(lowered);
        lenient().when(cb.equal(any(), any(Object.class))).thenReturn(p);
        lenient().when(cb.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(p);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(p);

        cap.getValue().toPredicate(root, query, cb);

        verify(cb).equal(path, CommentStatus.REMOVED);
        verify(cb).equal(path, "14");
        verify(cb).like(lowered, "%50\\%\\_off%", '\\');
    }

    @Test
    void getComments_specificationWithNoFilters_addsNoPredicates() {
        when(commentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getComments(PageRequest.of(0, 5), null, "  ", "");
        ArgumentCaptor<Specification> cap = ArgumentCaptor.forClass(Specification.class);
        verify(commentRepository).findAll(cap.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        cap.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), cb);

        verify(cb, never()).equal(any(), any(Object.class));
        verify(cb, never()).like(any(Expression.class), anyString(), any(Character.class));
    }

    @Test
    void escapeLike_escapesBackslashPercentUnderscore() {
        assertThat(AdminCommentServiceImpl.escapeLike("a\\b%c_d")).isEqualTo("a\\\\b\\%c\\_d");
    }

    // ── moderate ──────────────────────────────────────────────────────────────

    @Test
    void moderate_remove_setsStatusAndAuditFields() {
        VideoComment c = comment("14", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(videoSourceRepository.findAllById(Set.of(14L))).thenReturn(List.of(video(14L, "T")));
        UserDetailDto admin = UserDetailDto.builder().id(1L).email("admin@x.com").build();

        AdminCommentDto dto;
        try (MockedStatic<AppUtils> m = mockStatic(AppUtils.class, CALLS_REAL_METHODS)) {
            m.when(AppUtils::getCurrentUser).thenReturn(admin);
            dto = service.moderate(c.getId(),
                    req(CommentModerationAction.REMOVE, CommentModerationReason.SPAM, " link farm "));
        }

        assertThat(c.getStatus()).isEqualTo(CommentStatus.REMOVED);
        assertThat(c.getModerationReason()).isEqualTo(CommentModerationReason.SPAM);
        assertThat(c.getModerationNote()).isEqualTo("link farm");
        assertThat(c.getModeratedBy()).isEqualTo("admin@x.com");
        assertThat(c.getModeratedAt()).isNotNull();
        verify(commentRepository).save(c);
        assertThat(dto.getStatus()).isEqualTo(CommentStatus.REMOVED);
        assertThat(dto.getVideoTitle()).isEqualTo("T");
        assertThat(dto.getModeratedBy()).isEqualTo("admin@x.com");
    }

    @Test
    void moderate_removeWithoutCurrentUser_usesUnknown() {
        VideoComment c = comment("abc", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        try (MockedStatic<AppUtils> m = mockStatic(AppUtils.class, CALLS_REAL_METHODS)) {
            m.when(AppUtils::getCurrentUser).thenReturn(null);
            service.moderate(c.getId(), req(CommentModerationAction.REMOVE, CommentModerationReason.SPAM, null));
        }
        assertThat(c.getModeratedBy()).isEqualTo("unknown");
        assertThat(c.getModerationNote()).isNull();
    }

    @Test
    void moderate_removeOtherWithNote_ok() {
        VideoComment c = comment("abc", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        service.moderate(c.getId(), req(CommentModerationAction.REMOVE, CommentModerationReason.OTHER, "why"));

        assertThat(c.getStatus()).isEqualTo(CommentStatus.REMOVED);
    }

    @Test
    void moderate_removeOtherWithoutNote_returns400() {
        VideoComment c = comment("14", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.moderate(c.getId(),
                req(CommentModerationAction.REMOVE, CommentModerationReason.OTHER, "   ")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(commentRepository, never()).save(any());
        assertThat(c.getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }

    @Test
    void moderate_removeWithoutReason_returns400() {
        VideoComment c = comment("14", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.moderate(c.getId(), req(CommentModerationAction.REMOVE, null, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void moderate_unknownId_returns404() {
        UUID id = UUID.randomUUID();
        when(commentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderate(id, req(CommentModerationAction.RESTORE, null, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void moderate_removeAlreadyRemoved_isIdempotentAndKeepsOriginalRecord() {
        VideoComment c = comment("abc", "a@b.com");
        c.setStatus(CommentStatus.REMOVED);
        c.setModerationReason(CommentModerationReason.SPAM);
        c.setModeratedBy("first@x.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        service.moderate(c.getId(), req(CommentModerationAction.REMOVE, CommentModerationReason.SEXUAL, null));

        assertThat(c.getModerationReason()).isEqualTo(CommentModerationReason.SPAM);
        assertThat(c.getModeratedBy()).isEqualTo("first@x.com");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void moderate_restore_setsVisibleAndKeepsHistory() {
        VideoComment c = comment("abc", "a@b.com");
        c.setStatus(CommentStatus.REMOVED);
        c.setModerationReason(CommentModerationReason.SPAM);
        c.setModeratedBy("admin@x.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        AdminCommentDto dto = service.moderate(c.getId(), req(CommentModerationAction.RESTORE, null, null));

        assertThat(c.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(c.getModerationReason()).isEqualTo(CommentModerationReason.SPAM);
        assertThat(c.getModeratedBy()).isEqualTo("admin@x.com");
        verify(commentRepository).save(c);
        assertThat(dto.getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }

    @Test
    void moderate_restoreAlreadyVisible_isIdempotent() {
        VideoComment c = comment("abc", "a@b.com");
        when(commentRepository.findById(c.getId())).thenReturn(Optional.of(c));

        service.moderate(c.getId(), req(CommentModerationAction.RESTORE, null, null));

        verify(commentRepository, never()).save(any());
    }
}
