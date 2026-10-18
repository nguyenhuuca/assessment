package com.canhlabs.funnyapp.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Moves a video right after {@code afterVideoId}; null = move to the top. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveVideoRequest {
    private Long afterVideoId;
}
