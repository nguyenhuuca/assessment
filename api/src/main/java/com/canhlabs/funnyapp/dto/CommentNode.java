package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentNode {
    private UUID id;
    private String videoId;
    private String userId;
    private String guestName;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
    private String parentId;
    private List<CommentNode> replies;
}
