package com.canhlabs.funnyapp.dto.collection;

import com.canhlabs.funnyapp.enums.CollectionVisibility;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create (name required) and partial update (any field may be null = unchanged).
 * Blank/whitespace-only names are rejected by the service after trimming.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionRequest {
    public static final int MAX_NAME = 60;

    @Size(max = MAX_NAME)
    private String name;

    private CollectionVisibility visibility;
}
