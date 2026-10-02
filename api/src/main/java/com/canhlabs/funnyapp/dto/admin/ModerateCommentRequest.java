package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.CommentModerationAction;
import com.canhlabs.funnyapp.enums.CommentModerationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerateCommentRequest {
    @NotNull
    private CommentModerationAction action;
    private CommentModerationReason reason;
    @Size(max = 500)
    private String note;
}
