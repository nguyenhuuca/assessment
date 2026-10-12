package com.canhlabs.funnyapp.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class HardDeleteCommentsRequest {
    @NotEmpty
    @Size(max = BulkModerateCommentRequest.MAX_IDS)
    private List<@NotNull UUID> ids;
}
