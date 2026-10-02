package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.CommentModerationAction;
import com.canhlabs.funnyapp.enums.CommentModerationReason;
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
public class BulkModerateCommentRequest {
    public static final int MAX_IDS = 100;

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull UUID> ids;
    @NotNull
    private CommentModerationAction action;
    private CommentModerationReason reason;
    @Size(max = 500)
    private String note;
}
