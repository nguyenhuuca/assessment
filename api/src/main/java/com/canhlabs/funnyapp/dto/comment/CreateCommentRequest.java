package com.canhlabs.funnyapp.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Author identity is never taken from the body: it comes from the JWT (logged-in user)
 * or the X-Guest-Token header (guest).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {
    private String guestName;          // optional
    @NotBlank
    @Size(max = 2000)
    private String content;
    private String parentId;             // optional
}
