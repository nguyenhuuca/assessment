package com.canhlabs.funnyapp.dto.collection;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Duplicates are allowed in the payload; the service dedupes in request order. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionBulkRequest {
    public static final int MAX_IDS = 100;

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull Long> videoIds;
}
