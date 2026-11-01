package com.canhlabs.funnyapp.dto.suggestion;

import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.List;

/** Bulk approve or reject of pending suggestions (ADR-0036). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionBulkRequest {
    public static final int MAX_IDS = 100;

    public enum Action { APPROVE, REJECT }

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull Long> ids;
    @NotNull
    private Action action;
    /** REJECT only. */
    private SuggestionRejectReason reason;
    /** REJECT only; required when reason is OTHER (checked by the service, error.suggestion.noteRequired). */
    @Size(max = 300)
    private String note;

    /** Ids without duplicates, in request order. */
    public List<Long> distinctIds() {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    /** REJECT needs a reason, checked before any row is touched. */
    @JsonIgnore
    @AssertTrue(message = "reason is required for REJECT")
    public boolean isReasonPresentForReject() {
        return action != Action.REJECT || reason != null;
    }
}
