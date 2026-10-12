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
public class HardDeleteCommentsResultDto {
    /** Distinct ids received. */
    private int requested;
    /** Requested comments that were deleted. */
    private int deleted;
    /** Replies (any depth) deleted along with them. */
    private int repliesDeleted;
    private List<UUID> notFound;
}
