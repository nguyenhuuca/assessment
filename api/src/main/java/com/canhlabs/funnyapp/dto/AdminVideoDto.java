package com.canhlabs.funnyapp.dto;

import com.canhlabs.funnyapp.enums.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVideoDto {
    private Long id;
    private String title;
    private String thumbnailPath;
    private String creatorEmail;
    private VideoStatus status;
    private Long viewCount;
    private Instant createdAt;
}
