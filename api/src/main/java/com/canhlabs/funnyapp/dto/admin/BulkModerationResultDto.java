package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkModerationResultDto {
    /** Distinct ids received. */
    private int requested;
    /** Comments whose status actually changed. */
    private int updated;
    /** Comments already in the target state. */
    private int unchanged;
    private List<UUID> notFound;
}
