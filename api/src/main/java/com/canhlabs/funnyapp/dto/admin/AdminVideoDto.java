package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.enums.VideoVisibility;
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
    private VideoVisibility visibility;
    /** Derived (visibility == HIDDEN); kept for one release for the legacy admin page. */
    private boolean hidden;
    private Long viewCount;
    private Integer priority;
    private Instant createdAt;
    /** Null for videos that are not on Google Drive (HLS is Drive only). */
    private HlsStatus hlsStatus;
    private String hlsError;
}
