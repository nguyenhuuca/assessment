package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVideoBulkRequest {
    public static final int MAX_IDS = 100;

    public enum Action { STATUS, VISIBILITY, DELETE }

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull Long> ids;
    @NotNull
    private Action action;
    private VideoStatus status;
    private VideoVisibility visibility;
    /** DELETE only; Drive files are never deleted in bulk. */
    private Boolean deleteLocalFile;

    public boolean shouldDeleteLocalFile() {
        return Boolean.TRUE.equals(deleteLocalFile);
    }

    /** Ids without duplicates, in request order. */
    public List<Long> distinctIds() {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    /** The parameter the chosen action needs must be present, checked before any row is touched. */
    @JsonIgnore
    @AssertTrue(message = "action parameter is missing")
    public boolean isActionParamPresent() {
        if (action == null) {
            return true;
        }
        return switch (action) {
            case STATUS -> status != null;
            case VISIBILITY -> visibility != null;
            case DELETE -> true;
        };
    }
}
