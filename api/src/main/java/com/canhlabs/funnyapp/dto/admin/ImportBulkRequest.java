package com.canhlabs.funnyapp.dto.admin;

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
public class ImportBulkRequest {
    public static final int MAX_IDS = 100;

    public enum Action { CANCEL, RETRY }

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull Long> ids;
    @NotNull
    private Action action;

    /** Ids without duplicates, in request order. */
    public List<Long> distinctIds() {
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
