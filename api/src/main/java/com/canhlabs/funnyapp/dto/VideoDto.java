package com.canhlabs.funnyapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDto extends BaseDto {
    private Long id;
    private String userShared;
    private String title;
    private String desc;
    private String embedLink;
    private String urlLink;
    private String fileId;
}
