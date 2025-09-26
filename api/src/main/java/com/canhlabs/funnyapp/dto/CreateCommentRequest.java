package com.canhlabs.funnyapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@UserOrGuest
public class CreateCommentRequest {
    private String userId;             // optional
    private String guestName;          // optional
    @NotBlank
    private String content;
    private String parentId;             // optional
}
