package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionBulkRequest;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionBulkRequest.Action;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.service.VideoSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionBulkServiceImplTest {

    @Mock VideoSuggestionService suggestionService;
    AppProperties props;
    SuggestionBulkServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getVideoImport().setEnabled(true);
        service = new SuggestionBulkServiceImpl();
        service.injectDependencies(suggestionService, props);
    }

    private static SuggestionBulkRequest approve(Long... ids) {
        return SuggestionBulkRequest.builder().ids(List.of(ids)).action(Action.APPROVE).build();
    }

    @Test
    void approve_mixedOutcomes_bucketsEveryId() {
        // explicit stub: strict stubs reject a call with args that differ from the other stubs
        when(suggestionService.approve(1L, null, 7L)).thenReturn(null);
        when(suggestionService.approve(2L, null, 7L))
                .thenThrow(CustomException.of(HttpStatus.CONFLICT, 4093, "error.suggestion.notPending"));
        when(suggestionService.approve(3L, null, 7L))
                .thenThrow(CustomException.of(HttpStatus.NOT_FOUND, 4042, "error.suggestion.notFound"));
        when(suggestionService.approve(4L, null, 7L))
                .thenThrow(CustomException.of(HttpStatus.CONFLICT, 4094, "error.import.duplicateActive"));

        BulkResultDto result = service.bulk(approve(1L, 2L, 3L, 4L, 1L), 7L);

        assertThat(result.getRequested()).isEqualTo(4);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getNotFound()).containsExactly("3");
        assertThat(result.getSkipped()).extracting("id", "code")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("2", "error.suggestion.notPending"),
                        org.assertj.core.groups.Tuple.tuple("4", "error.import.duplicateActive"));
        verify(suggestionService).approve(1L, null, 7L);
    }

    @Test
    void approve_importDisabled_failsWholeRequestBeforeAnyRow() {
        props.getVideoImport().setEnabled(false);

        assertThatThrownBy(() -> service.bulk(approve(1L, 2L), 7L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getMessageKey()).isEqualTo("error.import.disabled"));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void reject_otherWithoutNote_failsBeforeAnyRow() {
        SuggestionBulkRequest request = SuggestionBulkRequest.builder().ids(List.of(1L, 2L))
                .action(Action.REJECT).reason(SuggestionRejectReason.OTHER).note("  ").build();

        assertThatThrownBy(() -> service.bulk(request, 7L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getMessageKey()).isEqualTo("error.suggestion.noteRequired"));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void reject_callsSingleRowWithReasonAndNote() {
        SuggestionBulkRequest request = SuggestionBulkRequest.builder().ids(List.of(1L))
                .action(Action.REJECT).reason(SuggestionRejectReason.OTHER).note("spam").build();

        BulkResultDto result = service.bulk(request, 7L);

        assertThat(result.getUpdated()).isEqualTo(1);
        verify(suggestionService).reject(1L, SuggestionRejectReason.OTHER, "spam", 7L);
    }

    @Test
    void otherCustomError_isSkippedNotNotFound() {
        when(suggestionService.approve(1L, null, 7L))
                .thenThrow(CustomException.of(HttpStatus.BAD_REQUEST, 4001, "error.other"));

        BulkResultDto result = service.bulk(approve(1L), 7L);

        assertThat(result.getNotFound()).isEmpty();
        assertThat(result.getSkipped()).hasSize(1);
    }
}
