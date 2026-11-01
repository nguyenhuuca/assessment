package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoCollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionBulkServiceImplTest {
    private static final UUID COLLECTION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock VideoCollectionService collectionService;
    @Mock VideoSourceRepository videoSourceRepository;
    CollectionBulkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CollectionBulkServiceImpl();
        service.injectCollectionService(collectionService);
        service.injectVideoSourceRepository(videoSourceRepository);
    }

    private static CustomException error(HttpStatus status, String key) {
        return CustomException.of(status, status.value(), key);
    }

    @Test
    void mixedOutcomes_addedAlreadyIneligibleMissingAndCapReached() {
        when(collectionService.addVideo(COLLECTION, 1L)).thenReturn(true);
        when(collectionService.addVideo(COLLECTION, 2L)).thenReturn(false);
        when(collectionService.addVideo(COLLECTION, 3L)).thenThrow(error(HttpStatus.NOT_FOUND, "error.video.notFound"));
        when(collectionService.addVideo(COLLECTION, 4L)).thenThrow(error(HttpStatus.NOT_FOUND, "error.video.notFound"));
        when(collectionService.addVideo(COLLECTION, 5L)).thenReturn(true);
        when(collectionService.addVideo(COLLECTION, 6L)).thenThrow(error(HttpStatus.CONFLICT, "error.collection.full"));
        when(videoSourceRepository.existsById(3L)).thenReturn(true);
        when(videoSourceRepository.existsById(4L)).thenReturn(false);

        BulkResultDto result = service.addVideos(COLLECTION, List.of(1L, 2L, 3L, 4L, 5L, 6L));

        assertThat(result.getRequested()).isEqualTo(6);
        assertThat(result.getUpdated()).isEqualTo(2);
        assertThat(result.getUnchanged()).isEqualTo(1);
        assertThat(result.getNotFound()).containsExactly("4");
        assertThat(result.getSkipped()).hasSize(2);
        assertThat(result.getSkipped().get(0).getId()).isEqualTo("3");
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.collection.videoNotEligible");
        assertThat(result.getSkipped().get(1).getId()).isEqualTo("6");
        assertThat(result.getSkipped().get(1).getCode()).isEqualTo("error.collection.full");
    }

    @Test
    void notManageable_failsWholeRequest_noPerRowCalls() {
        CustomException notFound = error(HttpStatus.NOT_FOUND, "error.collection.notFound");
        doThrow(notFound).when(collectionService).requireManageable(COLLECTION);

        assertThatThrownBy(() -> service.addVideos(COLLECTION, List.of(1L, 2L))).isSameAs(notFound);

        verify(collectionService, never()).addVideo(any(), any());
        verifyNoMoreInteractions(videoSourceRepository);
    }

    @Test
    void duplicateIds_processedOnce() {
        when(collectionService.addVideo(COLLECTION, 1L)).thenReturn(true);

        BulkResultDto result = service.addVideos(COLLECTION, List.of(1L, 1L, 1L));

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(1);
        verify(collectionService).addVideo(COLLECTION, 1L);
    }

    @Test
    void otherCustomException_isSkippedWithItsKey_andUnexpectedIsMasked() {
        when(collectionService.addVideo(COLLECTION, 1L)).thenThrow(error(HttpStatus.UNAUTHORIZED, "error.auth.required"));
        when(collectionService.addVideo(COLLECTION, 2L)).thenThrow(new RuntimeException("db secret"));

        BulkResultDto result = service.addVideos(COLLECTION, List.of(1L, 2L));

        assertThat(result.getSkipped()).hasSize(2);
        assertThat(result.getSkipped().get(0).getCode()).isEqualTo("error.auth.required");
        assertThat(result.getSkipped().get(1).getCode()).isEqualTo("error.unexpected");
        assertThat(result.getSkipped().get(1).getMessage()).doesNotContain("db secret");
        verifyNoMoreInteractions(videoSourceRepository);
    }
}
