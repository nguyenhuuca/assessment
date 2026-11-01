package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest;
import com.canhlabs.funnyapp.dto.admin.AdminVideoBulkRequest.Action;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ResourceNotFoundException;
import com.canhlabs.funnyapp.service.AdminVideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminVideoBulkServiceImplTest {

    @Mock AdminVideoService adminVideoService;
    AdminVideoBulkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminVideoBulkServiceImpl();
        service.injectAdminVideoService(adminVideoService);
    }

    private static AdminVideoBulkRequest statusRequest(Long... ids) {
        return AdminVideoBulkRequest.builder().ids(List.of(ids)).action(Action.STATUS)
                .status(VideoStatus.PUBLISHED).build();
    }

    @Test
    void status_mixedOutcomes_bucketsEveryId() {
        when(adminVideoService.updateStatus(1L, VideoStatus.PUBLISHED)).thenReturn(true);
        when(adminVideoService.updateStatus(2L, VideoStatus.PUBLISHED)).thenReturn(false);
        when(adminVideoService.updateStatus(3L, VideoStatus.PUBLISHED))
                .thenThrow(new ResourceNotFoundException("Video not found: 3"));
        when(adminVideoService.updateStatus(4L, VideoStatus.PUBLISHED))
                .thenThrow(CustomException.of(HttpStatus.CONFLICT, 409, "error.video.busy"));

        BulkResultDto result = service.bulk(statusRequest(1L, 2L, 3L, 4L));

        assertThat(result.getRequested()).isEqualTo(4);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getUnchanged()).isEqualTo(1);
        assertThat(result.getNotFound()).containsExactly("3");
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getId()).isEqualTo("4");
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.video.busy");
    }

    @Test
    void duplicateIds_processedOnce() {
        when(adminVideoService.updateStatus(1L, VideoStatus.PUBLISHED)).thenReturn(true);

        BulkResultDto result = service.bulk(statusRequest(1L, 1L, 1L));

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(1);
        verify(adminVideoService).updateStatus(1L, VideoStatus.PUBLISHED);
    }

    @Test
    void unexpectedException_skippedAndNextRowStillProcessed() {
        when(adminVideoService.updateStatus(1L, VideoStatus.PUBLISHED)).thenThrow(new RuntimeException("db secret"));
        when(adminVideoService.updateStatus(2L, VideoStatus.PUBLISHED)).thenReturn(true);

        BulkResultDto result = service.bulk(statusRequest(1L, 2L));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.unexpected");
        assertThat(result.getSkipped().get(0).getMessage()).doesNotContain("db secret");
    }

    @Test
    void visibility_updatesEachRow() {
        when(adminVideoService.updateVisibility(1L, VideoVisibility.HIDDEN)).thenReturn(true);
        when(adminVideoService.updateVisibility(2L, VideoVisibility.HIDDEN)).thenReturn(false);

        BulkResultDto result = service.bulk(AdminVideoBulkRequest.builder().ids(List.of(1L, 2L))
                .action(Action.VISIBILITY).visibility(VideoVisibility.HIDDEN).build());

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getUnchanged()).isEqualTo(1);
    }

    @Test
    void delete_neverDeletesDriveFile_andLockedFileIsSkipped() {
        // lenient: rows 1 and 3 hit the same method with other args, which strict stubs would reject
        lenient().doThrow(new IllegalStateException("File x is being synced, try again later"))
                .when(adminVideoService).deleteVideo(2L, true, false);

        BulkResultDto result = service.bulk(AdminVideoBulkRequest.builder().ids(List.of(1L, 2L, 3L))
                .action(Action.DELETE).deleteLocalFile(true).build());

        assertThat(result.getUpdated()).isEqualTo(2);
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getId()).isEqualTo("2");
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.conflict");
        // CWE-209: the file id in the exception text never reaches the client
        assertThat(result.getSkipped().get(0).getMessage()).doesNotContain("File x");
        verify(adminVideoService, never()).deleteVideo(anyLong(), anyBoolean(), eq(true));
        verify(adminVideoService).deleteVideo(3L, true, false);
    }

    @Test
    void delete_plainIllegalArgument_isSkippedNotNotFound() {
        // a message that merely looks like not-found must not be bucketed as notFound
        doThrow(new IllegalArgumentException("Video not found: but a plain IAE")).when(adminVideoService).deleteVideo(1L, false, false);

        BulkResultDto result = service.bulk(AdminVideoBulkRequest.builder().ids(List.of(1L))
                .action(Action.DELETE).build());

        assertThat(result.getNotFound()).isEmpty();
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.unexpected");
        assertThat(result.getSkipped().get(0).getMessage()).isEqualTo("Unexpected error");
    }

    @Test
    void customExceptionWithoutKey_usesFallbackCode() {
        when(adminVideoService.updateStatus(1L, VideoStatus.PUBLISHED))
                .thenThrow(CustomException.builder().message("bad").build());

        BulkResultDto result = service.bulk(statusRequest(1L));

        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.request");
        assertThat(result.getSkipped().get(0).getMessage()).isEqualTo("bad");
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
