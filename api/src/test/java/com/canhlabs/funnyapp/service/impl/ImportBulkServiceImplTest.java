package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.admin.ImportBulkRequest;
import com.canhlabs.funnyapp.dto.admin.ImportBulkRequest.Action;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.VideoImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportBulkServiceImplTest {

    @Mock VideoImportService videoImportService;
    ImportBulkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImportBulkServiceImpl();
        service.injectVideoImportService(videoImportService);
    }

    private static ImportBulkRequest request(Action action, Long... ids) {
        return ImportBulkRequest.builder().ids(List.of(ids)).action(action).build();
    }

    private static CustomException legacy(HttpStatus status, int subCode, String message) {
        return CustomException.builder().status(status).subCode(subCode).message(message).build();
    }

    private static VideoImportJobDto job(long id) {
        return VideoImportJobDto.builder().id(id).build();
    }

    @Test
    void cancel_mixedOutcomes_bucketsEveryId() {
        when(videoImportService.cancel(1L)).thenReturn(job(1L));
        when(videoImportService.cancel(2L)).thenThrow(legacy(HttpStatus.CONFLICT, 4091, "INVALID_STATE"));
        when(videoImportService.cancel(3L)).thenThrow(legacy(HttpStatus.NOT_FOUND, 4041, "IMPORT_NOT_FOUND"));
        when(videoImportService.cancel(4L)).thenReturn(job(4L));

        BulkResultDto result = service.bulk(request(Action.CANCEL, 1L, 2L, 3L, 4L));

        assertThat(result.getRequested()).isEqualTo(4);
        assertThat(result.getUpdated()).isEqualTo(2);
        assertThat(result.getNotFound()).containsExactly("3");
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getId()).isEqualTo("2");
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    void retry_duplicateActive_isSkippedWithExistingKey_andNextRowStillRuns() {
        when(videoImportService.retry(1L)).thenThrow(legacy(HttpStatus.CONFLICT, 4092, "DUPLICATE_ACTIVE"));
        when(videoImportService.retry(2L)).thenReturn(job(2L));

        BulkResultDto result = service.bulk(request(Action.RETRY, 1L, 2L));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSkipped()).hasSize(1);
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("DUPLICATE_ACTIVE");
    }

    @Test
    void unexpectedFailure_isSkippedWithGenericCode_andDoesNotLeakText() {
        when(videoImportService.retry(1L)).thenThrow(new RuntimeException("jdbc secret detail"));
        when(videoImportService.retry(2L)).thenReturn(job(2L));

        BulkResultDto result = service.bulk(request(Action.RETRY, 1L, 2L));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo(BulkResultCollector.UNEXPECTED_CODE);
        assertThat(result.getSkipped().get(0).getMessage()).doesNotContain("secret");
    }

    @Test
    void keyedCustomException_passesThroughUnchanged() {
        when(videoImportService.cancel(1L)).thenThrow(CustomException.of(HttpStatus.CONFLICT, 409, "error.x"));

        BulkResultDto result = service.bulk(request(Action.CANCEL, 1L));

        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.x");
    }

    @Test
    void duplicateIds_areProcessedOnce() {
        when(videoImportService.cancel(5L)).thenReturn(job(5L));

        BulkResultDto result = service.bulk(request(Action.CANCEL, 5L, 5L));

        assertThat(result.getRequested()).isEqualTo(1);
        verify(videoImportService, times(1)).cancel(5L);
    }
}
