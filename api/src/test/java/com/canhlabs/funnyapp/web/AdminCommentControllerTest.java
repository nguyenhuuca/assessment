package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.admin.AdminCommentDto;
import com.canhlabs.funnyapp.dto.admin.ModerateCommentRequest;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.CommentModerationAction;
import com.canhlabs.funnyapp.enums.CommentStatus;
import com.canhlabs.funnyapp.service.AdminCommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdminCommentControllerTest {

    @Mock AdminCommentService adminCommentService;

    @InjectMocks AdminCommentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller.injectAdminCommentService(adminCommentService);
    }

    // ── comments ───────────────────────────────────────────────────────────────

    @Test
    void getComments_passesFiltersAndReturnsPage() {
        Page<AdminCommentDto> page = new PageImpl<>(List.of(AdminCommentDto.builder().videoId("1").build()));
        when(adminCommentService.getComments(any(), eq(CommentStatus.REMOVED), eq("q"), eq("1"))).thenReturn(page);

        ResponseEntity<ResultObjectInfo<Page<AdminCommentDto>>> response =
                controller.getComments(PageRequest.of(0, 20), CommentStatus.REMOVED, "q", "1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    void moderateComment_delegatesToService() {
        UUID id = UUID.randomUUID();
        ModerateCommentRequest req = ModerateCommentRequest.builder()
                .action(CommentModerationAction.RESTORE).build();
        when(adminCommentService.moderate(id, req)).thenReturn(AdminCommentDto.builder().id(id).build());

        ResponseEntity<ResultObjectInfo<AdminCommentDto>> response = controller.moderateComment(id, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getData().getId());
    }
}
